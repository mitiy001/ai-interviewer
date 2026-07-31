package com.aiinterviewer.skill;

import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.mapper.SkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后校验默认 Skill 是否就绪。
 * <p>
 * seed.sql 应已插入 id=1 的 "Java 资深面试官" skill（is_active=1）。
 * 若未就绪，仅告警不阻断启动（用户可后续手动导入 seed.sql）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillLoader {

    private final SkillMapper skillMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void checkDefaultSkill() {
        LambdaQueryWrapper<Skill> qw = new LambdaQueryWrapper<>();
        qw.eq(Skill::getIsActive, 1).last("LIMIT 1");
        Skill active = skillMapper.selectOne(qw);
        if (active == null) {
            log.warn("未发现激活的 Skill，请先执行 seed.sql 导入默认 Java skill");
            return;
        }
        log.info("Skill 就绪：id={} name={} position={}", active.getId(), active.getName(), active.getPosition());
    }
}
