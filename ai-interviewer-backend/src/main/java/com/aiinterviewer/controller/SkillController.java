package com.aiinterviewer.controller;

import com.aiinterviewer.common.Result;
import com.aiinterviewer.dto.resp.SkillResp;
import com.aiinterviewer.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Skill 判定标准 REST 接口
 * <p>
 * GET   /api/skill          列表
 * GET   /api/skill/{id}     详情
 * GET   /api/skill/active   当前激活
 * POST  /api/skill/{id}/activate  激活
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public Result<List<SkillResp>> list() {
        return Result.ok(skillService.list());
    }

    @GetMapping("/{id}")
    public Result<SkillResp> get(@PathVariable Long id) {
        return Result.ok(skillService.get(id));
    }

    @GetMapping("/active")
    public Result<SkillResp> getActive() {
        return Result.ok(skillService.getActive());
    }

    @PostMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        skillService.activate(id);
        return Result.ok();
    }
}
