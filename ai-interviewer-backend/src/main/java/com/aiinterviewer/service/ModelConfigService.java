package com.aiinterviewer.service;

import com.aiinterviewer.dto.req.ModelConfigReq;
import com.aiinterviewer.dto.resp.ModelConfigResp;
import com.aiinterviewer.dto.resp.ModelTestResp;

import java.util.List;

/**
 * 模型配置 Service
 */
public interface ModelConfigService {

    /** 列出当前用户的所有模型配置 */
    List<ModelConfigResp> list();

    /** 获取单个（脱敏） */
    ModelConfigResp get(Long id);

    /** 新增；若 isActive=1 自动取消其他激活 */
    Long create(ModelConfigReq req);

    /** 更新；若 isActive=1 自动取消其他激活 */
    void update(Long id, ModelConfigReq req);

    /** 删除 */
    void delete(Long id);

    /** 激活指定配置（其他自动取消激活） */
    void activate(Long id);

    /** 获取当前激活的原始实体（含 apiKey 原文，供内部调用，不脱敏） */
    com.aiinterviewer.entity.ModelConfig getActiveRaw();

    /** 测试已保存配置的连通性 */
    ModelTestResp test(Long id);

    /** 测试未保存表单的连通性（apiKey 必填） */
    ModelTestResp test(ModelConfigReq req);
}
