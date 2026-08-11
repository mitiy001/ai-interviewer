package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String position;
    /** 工程师等级：junior/mid/senior */
    private String level;
    /** 技能类型：TECH（技术面）/ HR（人事面），默认 TECH */
    private String type;
    private String promptTemplate;
    private String scoringDimensions;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}模板，包含评分维度和判定要点...\"\n          ></textarea>\n        </div>\n\n        <div class=\"row\" style=\"justify-content: flex-end; margin-top: 8px; gap: 8px;\">\n          <button class=\"btn btn-secondary\" @click=\"closeDialog\">取消</button>\n          <button class=\"btn\" :disabled=\"submitting\" @click=\"submit\">\n            {{ submitting ? '提交中…' : '保存' }}\n          </button>\n        </div>\n      </div>\n    </div>\n  </div>\n</template>\n\n<style scoped>\n.modal-mask {\n  position: fixed;\n  inset: 0;\n  background: var(--overlay-mask);\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  z-index: 100;\n  padding: 20px;\n}\n\n.modal {\n  width: 680px;\n  max-width: 100%;\n  max-height: 90vh;\n  overflow-y: auto;\n}\n\n.dim-header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 12px;\n  margin-bottom: 8px;\n}\n\n.dim-row {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  margin-bottom: 8px;\n}\n\n.dim-input {\n  flex: 1;\n}\n\n.dim-max {\n  width: 80px;\n  flex-shrink: 0;\n}\n\n.btn-xs {\n  font-size: 11px;\n  padding: 4px 10px;\n}\n\n.textarea {\n  resize: vertical;\n  font-family: var(--font-mono, 'SFMono-Regular', Consolas, monospace);\n  font-size: 12px;\n  line-height: 1.6;\n}\n\n/* ===== 移动端响应式 ===== */\n@media (max-width: 768px) {\n  .modal {\n    width: 94vw;\n    max-height: 94dvh;\n    padding: 16px;\n  }\n  .modal-mask {\n    padding: 0;\n  }\n  .dim-row {\n    flex-wrap: wrap;\n  }\n  .dim-max {\n    width: 70px;\n  }\n}\n\n@media (max-width: 480px) {\n  .modal {\n    width: 100vw;\n    max-height: 100dvh;\n    border-radius: 0;\n    padding: 14px;\n  }\n}\n</style>"}, {"path": "ai-interviewer-backend/src/main/java/com/aiinterviewer/entity/Skill.java", "content": "package com.aiinterviewer.entity;\n\nimport com.baomidou.mybatisplus.annotation.IdType;\nimport com.baomidou.mybatisplus.annotation.TableId;\nimport com.baomidou.mybatisplus.annotation.TableName;\nimport lombok.Data;\n\nimport java.time.LocalDateTime;\n\n@Data\n@TableName(\"skill\")\npublic class Skill {\n\n    @TableId(type = IdType.AUTO)\n    private Long id;\n    private Long userId;\n    private String name;\n    private String position;\n    /** 工程师等级：junior/mid/senior */\n    private String level;\n    /** 技能类型：TECH（技术面）/ HR（人事面），默认 TECH */\n    private String type;\n    private String promptTemplate;\n    private String scoringDimensions;\n    private Integer isActive;\n    private LocalDateTime createdAt;\n    private LocalDateTime updatedAt;\n}\n"}]