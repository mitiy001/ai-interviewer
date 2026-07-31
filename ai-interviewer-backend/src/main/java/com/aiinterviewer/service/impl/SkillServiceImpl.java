package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
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
        LambdaQueryWrapper<Skill> qw = new LambdaQueryWrapper<>();
        qw.eq(Skill::getUserId, UserContext.getUserId())
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
        // 再激活指定
        LambdaUpdateWrapper<Skill> upd = new LambdaUpdateWrapper<>();
        upd.eq(Skill::getId, id)
                .eq(Skill::getUserId, userId)
                .set(Skill::getIsActive, 1)
                .set(Skill::getUpdatedAt, LocalDateTime.now());
        skillMapper.update(null, upd);
        log.info("activate skill id={} userId={}", id, userId);
    }

    // ---------- private ----------

    private Skill mustGetOwned(Long id) {
        Skill entity = skillMapper.selectById(id);
        if (entity == null || !UserContext.getUserId().equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Skill 不存在");
        }
        return entity;
    }

    private Skill mustGetActive() {
        LambdaQueryWrapper<Skill> qw = new LambdaQueryWrapper<>();
        qw.eq(Skill::getUserId, UserContext.getUserId())
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
        resp.setPromptTemplate(entity.getPromptTemplate());
        resp.setScoringDimensions(parseDimensions(entity.getScoringDimensions()));
        resp.setIsActive(entity.getIsActive());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
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
