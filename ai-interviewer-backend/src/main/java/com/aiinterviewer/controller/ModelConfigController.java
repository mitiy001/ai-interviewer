package com.aiinterviewer.controller;

import com.aiinterviewer.common.Result;
import com.aiinterviewer.dto.req.ModelConfigReq;
import com.aiinterviewer.dto.resp.ModelConfigResp;
import com.aiinterviewer.dto.resp.ModelTestResp;
import com.aiinterviewer.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型配置 REST 接口
 * <p>
 * GET    /api/model-config          列表
 * GET    /api/model-config/{id}     详情
 * POST   /api/model-config          新增
 * PUT    /api/model-config/{id}     更新
 * POST   /api/model-config/{id}/activate  激活
 * POST   /api/model-config/{id}/test      测试已保存配置连通性
 * POST   /api/model-config/test           测试未保存表单连通性
 * DELETE /api/model-config/{id}     删除
 */
@RestController
@RequestMapping("/api/model-config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public Result<List<ModelConfigResp>> list() {
        return Result.ok(modelConfigService.list());
    }

    @GetMapping("/{id}")
    public Result<ModelConfigResp> get(@PathVariable Long id) {
        return Result.ok(modelConfigService.get(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ModelConfigReq req) {
        return Result.ok(modelConfigService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModelConfigReq req) {
        modelConfigService.update(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        modelConfigService.activate(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<ModelTestResp> testSaved(@PathVariable Long id) {
        return Result.ok(modelConfigService.test(id));
    }

    @PostMapping("/test")
    public Result<ModelTestResp> testUnsaved(@RequestBody ModelConfigReq req) {
        return Result.ok(modelConfigService.test(req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return Result.ok();
    }
}
