package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.JwtUtil;
import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.resp.UserResp;
import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.entity.User;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.mapper.SkillMapper;
import com.aiinterviewer.mapper.UserMapper;
import com.aiinterviewer.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户认证 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** 密码强度正则：至少 8 位，含字母+数字 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d).{8,}$");

    /** 用户名正则：2-20 位字母数字下划线 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,20}$");

    private final UserMapper userMapper;
    private final SkillMapper skillMapper;
    private final JwtUtil jwtUtil;

    @Override
    public String login(String username, String password) {
        // 1. 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 2. 校验状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 3. 校验密码
        if (!PASSWORD_ENCODER.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 5. 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 6. 设置 UserContext，使后续 getCurrentUser 能获取到当前用户
        UserContext.setUserId(user.getId());

        log.info("用户登录成功 userId={} username={}", user.getId(), username);
        return token;
    }

    @Override
    public UserResp getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        UserResp resp = new UserResp();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        resp.setLastLoginAt(user.getLastLoginAt());
        resp.setCreatedAt(user.getCreatedAt());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(String username, String password) {
        // 0. 校验当前用户是否为管理员
        checkAdmin();

        // 1. 参数校验
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名须为 2-20 位字母/数字/下划线");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码须至少 8 位，包含字母和数字");
        }

        // 2. 查重
        Long exist = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null && exist > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名已存在");
        }

        // 3. 创建用户（默认角色为 user）
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PASSWORD_ENCODER.encode(password));
        user.setStatus(1);
        user.setRole("user");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("管理员创建用户成功 userId={} username={}", user.getId(), username);

        // 4. 复制默认 Skill 到新用户
        copyDefaultSkills(user.getId());

        // 5. 生成 JWT（用于该用户下次登录）
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    /** 校验当前用户是否为管理员 */
    private void checkAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User admin = userMapper.selectById(userId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可执行此操作");
        }
    }

    /** 复制模板 Skill 到指定用户 */
    private void copyDefaultSkills(Long targetUserId) {
        List<Skill> templates = skillMapper.selectList(
                new LambdaQueryWrapper<Skill>().eq(Skill::getUserId, 0L));
        if (templates.isEmpty()) {
            log.warn("未找到模板 Skill（user_id=0），跳过复制");
            return;
        }
        for (Skill t : templates) {
            Skill copy = new Skill();
            copy.setUserId(targetUserId);
            copy.setName(t.getName());
            copy.setPosition(t.getPosition());
            copy.setLevel(t.getLevel());
            copy.setPromptTemplate(t.getPromptTemplate());
            copy.setScoringDimensions(t.getScoringDimensions());
            copy.setIsActive(0);
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            skillMapper.insert(copy);
        }
        log.info("已为 userId={} 复制 {} 条默认 Skill", targetUserId, templates.size());
    }
