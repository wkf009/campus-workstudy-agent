-- ============================================================
-- Agent 模块新增表（P2 智能求职推荐）
-- 说明：执行完 init.sql 后再执行本文件；向量数据存内存 SimpleVectorStore，
--       画像与推荐记录落库持久化。
-- ============================================================
USE workstudy;

-- 学生画像表（P2：画像 JSON 持久化，向量索引可随时重建）
DROP TABLE IF EXISTS `student_profile`;
CREATE TABLE `student_profile` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT '学生ID',
  `major`        VARCHAR(100) DEFAULT NULL COMMENT '专业',
  `grade`        VARCHAR(20)  DEFAULT NULL COMMENT '年级',
  `skill_tags`   JSON         DEFAULT NULL COMMENT '技能标签数组',
  `time_pref`    JSON         DEFAULT NULL COMMENT '可工作时段数组',
  `salary_pref`  VARCHAR(50)  DEFAULT NULL COMMENT '期望薪资',
  `profile_text` TEXT         DEFAULT NULL COMMENT '画像文本(供向量化/展示)',
  `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生画像表';

-- 推荐记录表（P2：推荐结果留痕，用于效果评估/面试展示）
DROP TABLE IF EXISTS `recommendation_log`;
CREATE TABLE `recommendation_log` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`    BIGINT NOT NULL COMMENT '学生ID',
  `job_id`     BIGINT NOT NULL COMMENT '岗位ID',
  `score`      DECIMAL(5,2) DEFAULT NULL COMMENT '精排分数',
  `reason`     VARCHAR(255) DEFAULT NULL COMMENT '推荐理由',
  `strategies` JSON DEFAULT NULL COMMENT '命中的召回策略',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  KEY `idx_user` (`user_id`),
  KEY `idx_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐记录表';

-- 审核报告表（P4：AI 预审/匹配度评估报告，Human-in-the-Loop 依据）
DROP TABLE IF EXISTS `audit_report`;
CREATE TABLE `audit_report` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `target_type` VARCHAR(20)  NOT NULL COMMENT '目标类型: job-岗位预审 / application-申请匹配',
  `target_id`   BIGINT       NOT NULL COMMENT '目标ID（岗位ID或申请ID）',
  `suggestion`  VARCHAR(20)  DEFAULT NULL COMMENT '岗位: PASS/SUPPLEMENT/REJECT；申请: 存匹配分',
  `score`       DECIMAL(5,2) DEFAULT NULL COMMENT '质量分/匹配分 0-100',
  `reasons`     JSON DEFAULT NULL COMMENT '理由/匹配点数组',
  `suggestions` JSON DEFAULT NULL COMMENT '修改建议/差距点数组',
  `risk_flags`  JSON DEFAULT NULL COMMENT '风险标记数组',
  `agent_model` VARCHAR(50)  DEFAULT NULL COMMENT '使用的模型',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  UNIQUE KEY `uk_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 审核报告表';
