-- 创建数据库
CREATE DATABASE IF NOT EXISTS workstudy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE workstudy;

-- 创建部门信息表
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `name` varchar(100) NOT NULL COMMENT '部门/学院名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门信息表';

-- 创建系统用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名/学号/工号',
  `password` varchar(255) NOT NULL COMMENT '加密后的密码',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `role` int NOT NULL DEFAULT '0' COMMENT '角色: 0-学生, 1-企业导师, 2-部门管理员, 3-超级管理员',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT '0' COMMENT '性别: 0-未知, 1-男, 2-女',
  `student_no` varchar(50) DEFAULT NULL COMMENT '学号/工号',
  `major` varchar(100) DEFAULT NULL COMMENT '专业',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `status` tinyint DEFAULT '1' COMMENT '状态: 1-正常, 0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 创建岗位信息表
DROP TABLE IF EXISTS `job`;
CREATE TABLE `job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `title` varchar(100) NOT NULL COMMENT '岗位标题',
  `description` text COMMENT '岗位详细描述',
  `requirements` text COMMENT '任职要求',
  `salary` decimal(10,2) DEFAULT NULL COMMENT '薪资/津贴',
  `location` varchar(100) DEFAULT NULL COMMENT '工作地点',
  `quota` int DEFAULT '1' COMMENT '招聘名额',
  `work_time` varchar(100) DEFAULT NULL COMMENT '工作时间',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `department_name` varchar(100) DEFAULT NULL COMMENT '所属部门名称(冗余)',
  `publisher_id` bigint DEFAULT NULL COMMENT '发布人ID',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待审批, 1-招聘中, 2-已结束, 3-拒绝',
  `remark` varchar(255) DEFAULT NULL COMMENT '审批备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_department_id` (`department_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位信息表';

-- 创建岗位申请表
DROP TABLE IF EXISTS `job_application`;
CREATE TABLE `job_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `job_id` bigint NOT NULL COMMENT '岗位ID',
  `user_id` bigint NOT NULL COMMENT '申请人ID (学生)',
  `resume_url` varchar(255) DEFAULT NULL COMMENT '简历链接',
  `cover_letter` text COMMENT '自荐信',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待审, 1-通过, 2-拒绝, 3-取消',
  `audit_remark` varchar(255) DEFAULT NULL COMMENT '审核备注',
  `apply_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `auditor_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `interview_time` datetime DEFAULT NULL COMMENT '面试时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位申请表';

-- 创建消息通知表
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `type` tinyint DEFAULT '0' COMMENT '类型: 0-系统通知, 1-岗位通知, 2-申请通知',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- 创建操作日志表
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `operation` varchar(100) DEFAULT NULL COMMENT '操作描述',
  `method` varchar(200) DEFAULT NULL COMMENT '方法名',
  `params` text COMMENT '请求参数',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 插入测试数据
-- 插入部门
INSERT INTO department (name, description) VALUES
('计算机学院', '计算机科学与技术学院'),
('学生处', '学生工作处'),
('图书馆', '校图书馆');

-- 插入管理员用户 (密码都是 123456，使用 BCrypt 加密)
INSERT INTO sys_user (username, password, real_name, role, phone, email, department_id, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 3, '13800138000', 'admin@example.com', NULL, 1),
('dept1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '部门管理员1', 2, '13800138001', 'dept1@example.com', 1, 1),
('student1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '学生1', 0, '13800138002', 'student1@example.com', 1, 1);

-- 插入测试岗位
INSERT INTO job (title, description, requirements, salary, location, quota, work_time, contact_person, contact_phone, department_id, department_name, publisher_id, status) VALUES
('图书馆助理', '协助图书馆日常管理工作', '认真负责', 25.00, '图书馆', 3, '每周工作10小时', '张老师', '13800138010', 3, '图书馆', 2, 1);

-- ============================================================
-- Agent 模块表（P2 智能求职推荐）：
-- 向量数据存内存 SimpleVectorStore（重启可重建），画像与推荐记录落库
-- ============================================================

-- 学生画像表
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

-- 推荐记录表
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
