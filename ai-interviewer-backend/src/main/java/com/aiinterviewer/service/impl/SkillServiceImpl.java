package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.req.SkillReq;
import com.aiinterviewer.dto.resp.SkillResp;
import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.mapper.SkillMapper;
import com.aiinterviewer.service.SkillService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SkillResp> list() {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Skill> qw = new LambdaQueryWrapper<>();
        qw.in(Skill::getUserId, List.of(0L, userId))
                .orderByDesc(Skill::getIsActive)
                .orderByAsc(Skill::getId);
        return skillMapper.selectList(qw).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public SkillResp get(Long id) {
        Skill entity = mustGetOwned(id);
        return toResp(entity);
    }

    @Override
    public SkillResp getActive() {
        return toResp(mustGetActive());
    }

    @Override
    public Skill getActiveRaw() {
        return mustGetActive();
    }

    @Override
    public Skill getByIdRaw(Long id) {
        Skill entity = skillMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Skill 不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        Skill entity = mustGetOwned(id);
        Long userId = UserContext.getUserId();
        // 先把当前用户所有 skill 置 0
        LambdaUpdateWrapper<Skill> reset = new LambdaUpdateWrapper<>();
        reset.eq(Skill::getUserId, userId)
                .eq(Skill::getIsActive, 1)
                .set(Skill::getIsActive, 0)
                .set(Skill::getUpdatedAt, LocalDateTime.now());
        skillMapper.update(null, reset);
        // 再激活指定（不按 userId 过滤，因为系统模板 userId=0）
        LambdaUpdateWrapper<Skill> upd = new LambdaUpdateWrapper<>();
        upd.eq(Skill::getId, id)
                .set(Skill::getIsActive, 1)
                .set(Skill::getUpdatedAt, LocalDateTime.now());
        skillMapper.update(null, upd);
        log.info("activate skill id={} userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SkillReq req) {
        // 校验
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "名称不能为空");
        }
        if (req.getPosition() == null || req.getPosition().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "职位不能为空");
        }
        if (req.getLevel() == null || req.getLevel().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "等级不能为空");
        }
        if (req.getPromptTemplate() == null || req.getPromptTemplate().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "提示词模板不能为空");
        }

        Skill entity = new Skill();
        entity.setUserId(UserContext.getUserId());
        entity.setName(req.getName().trim());
        entity.setPosition(req.getPosition().trim());
        entity.setLevel(req.getLevel().trim());
        entity.setType(req.getType() != null ? req.getType().trim() : "TECH");
        entity.setPromptTemplate(req.getPromptTemplate().trim());
        entity.setScoringDimensions(toDimensionsJson(req.getScoringDimensions()));
        entity.setIsActive(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.insert(entity);

        log.info("创建 skill id={} name={} userId={}", entity.getId(), entity.getName(), UserContext.getUserId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SkillReq req) {
        Skill entity = mustGetOwned(id);
        // 系统模板不可编辑
        if (entity.getUserId() == 0L) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统模板不可编辑，请复制后修改");
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            entity.setName(req.getName().trim());
        }
        if (req.getPosition() != null && !req.getPosition().isBlank()) {
            entity.setPosition(req.getPosition().trim());
        }
        if (req.getLevel() != null && !req.getLevel().isBlank()) {
            entity.setLevel(req.getLevel().trim());
        }
        if (req.getType() != null && !req.getType().isBlank()) {
            entity.setType(req.getType().trim());
        }
        if (req.getPromptTemplate() != null && !req.getPromptTemplate().isBlank()) {
            entity.setPromptTemplate(req.getPromptTemplate().trim());
        }
        if (req.getScoringDimensions() != null) {
            entity.setScoringDimensions(toDimensionsJson(req.getScoringDimensions()));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        log.info("更新 skill id={} userId={}", id, UserContext.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Skill entity = mustGetOwned(id);
        // 系统模板不可删除
        if (entity.getUserId() == 0L) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统模板不可删除");
        }
        skillMapper.deleteById(id);
        log.info("删除 skill id={} name={} userId={}", id, entity.getName(), UserContext.getUserId());
    }

    // ---------- private ----------

    private Skill mustGetOwned(Long id) {
        Skill entity = skillMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Skill 不存在");
        }
        // 系统模板（userId=0）对所有用户可见，但不可编辑/删除
        Long userId = UserContext.getUserId();
        if (!userId.equals(entity.getUserId()) && entity.getUserId() != 0L) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Skill 不存在");
        }
        return entity;
    }

    private Skill mustGetActive() {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Skill> qw = new LambdaQueryWrapper<>();
        qw.in(Skill::getUserId, List.of(0L, userId))
                .eq(Skill::getIsActive, 1).last("LIMIT 1");
        Skill active = skillMapper.selectOne(qw);
        if (active == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "未找到激活的 Skill，请先在 DB 初始化默认 Java skill");
        }
        return active;
    }

    private SkillResp toResp(Skill entity) {
        SkillResp resp = new SkillResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setPosition(entity.getPosition());
        resp.setLevel(entity.getLevel());
        resp.setType(entity.getType());
        resp.setPromptTemplate(entity.getPromptTemplate());
        resp.setScoringDimensions(parseDimensions(entity.getScoringDimensions()));
        resp.setIsActive(entity.getIsActive());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }

    private String toDimensionsJson(List<SkillReq.ScoringDimensionReq> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (Exception e) {
            log.warn("序列化 scoring_dimensions 失败: {}", e.getMessage());
            return "[]";
        }
    }

    private List<SkillResp.ScoringDimension> parseDimensions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SkillResp.ScoringDimension>>() {
            });
        } catch (Exception e) {
            log.warn("解析 scoring_dimensions 失败: {}", e.getMessage());
            return List.of();
        }
    }
}