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
