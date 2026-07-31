-- AI 面试官 数据库 schema
-- 一键创建：mysql -uroot -p123456 < schema.sql

CREATE DATABASE IF NOT EXISTS ai_interviewer
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ai_interviewer;


-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  username       VARCHAR(64) NOT NULL,
  password_hash  VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希密码',
  email          VARCHAR(128) DEFAULT NULL COMMENT '邮箱（可选，用于找回密码）',
  status         TINYINT NOT NULL DEFAULT 1 COMMENT '1正常/0禁用',
  last_login_at  DATETIME DEFAULT NULL COMMENT '最后登录时间',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- ⚠ 已有库升级语句（老库必须执行）
# ALTER TABLE user ADD COLUMN password_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希密码' AFTER username;
# ALTER TABLE user ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱' AFTER password_hash;
# ALTER TABLE user ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常/0禁用' AFTER email;
# ALTER TABLE user ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER status;
-- 更新 default 用户密码为 123456（BCrypt）
# UPDATE user SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' WHERE id = 1;

-- 2. 模型 API 配置
CREATE TABLE IF NOT EXISTS model_config (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  provider        VARCHAR(32) NOT NULL DEFAULT 'openai-compatible',
  api_key         VARCHAR(256) NOT NULL,
  model           VARCHAR(64) NOT NULL,
  endpoint        VARCHAR(256) NOT NULL,
  judge_model     VARCHAR(64) DEFAULT NULL COMMENT '预留独立 Judge 模型名',
  judge_endpoint  VARCHAR(256) DEFAULT NULL COMMENT '预留独立 Judge 端点',
  tts_endpoint    VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务端点（Qwen3-TTS DashScope: https://dashscope.aliyuncs.com/api/v1，留空使用默认）',
  tts_api_key     VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务 API Key（为空则复用 api_key）',
  tts_model       VARCHAR(64) DEFAULT NULL COMMENT 'TTS 模型名（如 cosyvoice-v1 免费 / cosyvoice-v2 / cosyvoice-v3-plus）',
  tts_voice       VARCHAR(64) DEFAULT NULL COMMENT 'TTS 音色（v1: longhua/longxiaoxia/longshu；v3: Vivian/Serena）',
  is_active       TINYINT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型 API 配置';

-- ⚠ 已有库升级语句（首次建库可忽略，老库必须执行）
-- 若 model_config 表是早期创建的（无 tts_* 列），取消下面 4 行注释执行一次以补列：
-- ALTER TABLE model_config ADD COLUMN tts_endpoint VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务端点' AFTER judge_endpoint;
-- ALTER TABLE model_config ADD COLUMN tts_api_key VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务 API Key（为空则复用 api_key）' AFTER tts_endpoint;
-- ALTER TABLE model_config ADD COLUMN tts_model VARCHAR(64) DEFAULT NULL COMMENT 'TTS 模型名' AFTER tts_api_key;
-- ALTER TABLE model_config ADD COLUMN tts_voice VARCHAR(64) DEFAULT NULL COMMENT 'TTS 音色' AFTER tts_model;

-- 3. Skill 判定标准（结构化 prompt + 评分维度）
CREATE TABLE IF NOT EXISTS skill (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  user_id             BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户，0=系统模板',
  name                VARCHAR(64) NOT NULL,
  position            VARCHAR(32) NOT NULL DEFAULT 'default',
  level               VARCHAR(16) NOT NULL DEFAULT 'mid' COMMENT '工程师等级 junior/mid/senior',
  prompt_template     TEXT NOT NULL,
  scoring_dimensions  JSON NOT NULL,
  is_active           TINYINT NOT NULL DEFAULT 0,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_position_active (position, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='判定标准 Skill';

-- ⚠ 已有库升级语句（首次建库可忽略，老库必须执行）
-- 若 skill 表是早期创建的（无 level 列），取消下面一行注释执行一次以补列：
# ALTER TABLE skill ADD COLUMN level VARCHAR(16) NOT NULL DEFAULT 'mid' COMMENT '工程师等级 junior/mid/senior' AFTER position;
-- 若 skill 表无 user_id 列，执行：
# ALTER TABLE skill ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户，0=系统模板' AFTER id;
# ALTER TABLE skill ADD INDEX idx_user (user_id);
-- 已有 skill 设为模板
# UPDATE skill SET user_id = 0 WHERE id IN (1,2,3);
-- 4. 简历
CREATE TABLE IF NOT EXISTS resume (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  filename     VARCHAR(256) NOT NULL,
  raw_text     LONGTEXT,
  parsed_text  LONGTEXT,
  uploaded_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历';

-- 5. 题库
CREATE TABLE IF NOT EXISTS question_bank (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  name        VARCHAR(128) NOT NULL,
  source      VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT 'seed/user',
  description VARCHAR(512) DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库';

-- 6. 题目
CREATE TABLE IF NOT EXISTS question (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  bank_id          BIGINT NOT NULL,
  type             VARCHAR(16) NOT NULL DEFAULT 'theory' COMMENT 'theory/scenario/project',
  difficulty       TINYINT NOT NULL DEFAULT 1 COMMENT '1简单/2中等/3困难',
  content          TEXT NOT NULL,
  standard_answer  TEXT,
  scoring_points   JSON,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bank (bank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目';

-- 7. 面试主记录
CREATE TABLE IF NOT EXISTS interview_record (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  user_id          BIGINT NOT NULL,
  model_config_id  BIGINT NOT NULL,
  skill_id         BIGINT NOT NULL,
  resume_id        BIGINT DEFAULT NULL,
  bank_id          BIGINT NOT NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/FINISHED/ABORTED',
  max_turns        INT NOT NULL DEFAULT 5 COMMENT '本轮面试轮次上限',
  total_score      INT DEFAULT NULL,
  start_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_time         DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试主记录';

-- ⚠ 已有库升级语句（首次建库可忽略，老库必须执行）
-- 若 interview_record 表是早期创建的，取消下面一行注释执行一次以补列：
-- ALTER TABLE interview_record ADD COLUMN max_turns INT NOT NULL DEFAULT 5 COMMENT '本轮面试轮次上限' AFTER status;

-- 8. 每题回答与判定
CREATE TABLE IF NOT EXISTS answer_record (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  interview_id  BIGINT NOT NULL,
  question_id   BIGINT NOT NULL,
  turn_index    INT NOT NULL,
  user_answer   TEXT,
  ai_question   TEXT NOT NULL,
  score         INT,
  judge_reason  TEXT,
  answered_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_interview (interview_id),
  KEY idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每题回答与判定';

-- 9. 面试报告
CREATE TABLE IF NOT EXISTS interview_report (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  interview_id       BIGINT NOT NULL,
  total_score        INT,
  summary            TEXT,
  improvement_points JSON,
  generated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview (interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试报告';
