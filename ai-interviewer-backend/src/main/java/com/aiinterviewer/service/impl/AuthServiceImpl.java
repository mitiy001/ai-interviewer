package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.CaptchaService;
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
    private final CaptchaService captchaService;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String register(String username, String password, String captchaToken, String captchaCode) {
        // 1. 参数校验
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名须为 2-20 位字母/数字/下划线");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码须至少 8 位，包含字母和数字");
        }

        // 2. 验证码校验
        if (!captchaService.validate(captchaToken, captchaCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期");
        }

        // 3. 查重
        Long exist = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null && exist > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名已存在");
        }

        // 4. 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PASSWORD_ENCODER.encode(password));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户注册成功 userId={} username={}", user.getId(), username);

        // 5. 复制默认 Skill 到新用户
        copyDefaultSkills(user.getId());

        // 6. 设置 UserContext，使后续 getCurrentUser 能获取到当前用户
        UserContext.setUserId(user.getId());

        // 7. 生成 JWT，注册后自动登录
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("注册后自动登录 userId={} username={}", user.getId(), username);
        return token;
    }

    @Override
    public String login(String username, String password, String captchaToken, String captchaCode) {
        // 1. 验证码校验
        if (!captchaService.validate(captchaToken, captchaCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期");
        }

        // 2. 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 3. 校验状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 4. 校验密码
        if (!PASSWORD_ENCODER.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 5. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 6. 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 7. 设置 UserContext，使后续 getCurrentUser 能获取到当前用户
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
        resp.setLastLoginAt(user.getLastLoginAt());
        resp.setCreatedAt(user.getCreatedAt());
        return resp;
    }

    /** 复制模板 Skill 到指定用户 */
    private void copyDefaultSkills(Long targetUserId) {
        // 模板 Skill 的 user_id 为 0（系统默认）
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
}
