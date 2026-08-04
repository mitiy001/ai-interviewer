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

-- ALTER TABLE user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT 'admin/user' AFTER status;
# ALTER TABLE user ADD COLUMN password_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希密码' AFTER username;
# ALTER TABLE user ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱' AFTER password_hash;
# ALTER TABLE user ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常/0禁用' AFTER email;
# ALTER TABLE user ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER status;
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
  tts_endpoint    VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务端点',
  tts_api_key     VARCHAR(256) DEFAULT NULL COMMENT 'TTS 服务 API Key',
  tts_model       VARCHAR(64) DEFAULT NULL COMMENT 'TTS 模型名',
  tts_voice       VARCHAR(64) DEFAULT NULL COMMENT 'TTS 音色',
  is_active       TINYINT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型 API 配置';

-- 3. Skill 判定标准
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

-- 已有库升级语句
-- ALTER TABLE interview_record ADD COLUMN max_turns INT NOT NULL DEFAULT 5 COMMENT '本轮面试轮次上限' AFTER status;
-- ALTER TABLE interview_record ADD COLUMN context LONGTEXT DEFAULT NULL COMMENT '面试状态上下文JSON（用于断线重连恢复）' AFTER total_score;

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
