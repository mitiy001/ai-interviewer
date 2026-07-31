package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.SkillResp;
import com.aiinterviewer.entity.Skill;

import java.util.List;

/**
 * Skill 判定标准 Service
 */
public interface SkillService {

    /** 列出所有 skill */
    List<SkillResp> list();

    /** 获取单个 */
    SkillResp get(Long id);

    /** 获取当前激活的 skill（响应） */
    SkillResp getActive();

    /** 获取当前激活的 skill 原始实体（供 graph 节点内部使用） */
    Skill getActiveRaw();

    /** 按 id 获取 skill 原始实体（供指定 skill 面试使用，供 graph 节点内部使用） */
    Skill getByIdRaw(Long id);

    /** 激活指定 skill（其他自动取消激活） */
    void activate(Long id);
}
