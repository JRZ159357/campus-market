-- =============================================================
-- 校园二手交易平台 · V1 数据库脚本
-- 模块：用户认证（含学生认证审核）
-- 数据库：campus_market  字符集：utf8mb4
-- 说明：全部表采用逻辑外键，不建物理外键约束；
--       公共字段 id / create_time / update_time / deleted 齐全。
-- =============================================================

CREATE DATABASE IF NOT EXISTS `campus_market`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 用户主表：小程序用户与后台账号共用一张表，以 role 区分
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `open_id`           varchar(64)          DEFAULT NULL    COMMENT '微信openid，后台账号为空',
  `union_id`          varchar(64)          DEFAULT NULL    COMMENT '微信unionid',
  `username`          varchar(50)          DEFAULT NULL    COMMENT '后台登录账号，小程序用户为空',
  `password`          varchar(100)         DEFAULT NULL    COMMENT '后台密码(BCrypt)，小程序用户为空',
  `nickname`          varchar(50) NOT NULL DEFAULT '微信用户' COMMENT '昵称',
  `avatar`            varchar(500)         DEFAULT NULL    COMMENT '头像地址',
  `phone`             varchar(20)          DEFAULT NULL    COMMENT '手机号',
  `role`              varchar(20) NOT NULL DEFAULT 'user'  COMMENT '角色:user普通用户 student认证学生 customer_service客服 admin管理员',
  `status`            tinyint     NOT NULL DEFAULT 0       COMMENT '账号状态:0正常 1封禁',
  `student_verified`  tinyint     NOT NULL DEFAULT 0       COMMENT '学生认证:0未认证 1已认证',
  `credit_score`      int         NOT NULL DEFAULT 100     COMMENT '信誉分，初始100',
  `publish_limit`     int         NOT NULL DEFAULT 0       COMMENT '在售商品数量上限，认证学生默认10',
  `last_login_time`   datetime             DEFAULT NULL    COMMENT '最后登录时间',
  `create_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           tinyint     NOT NULL DEFAULT 0       COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role_status` (`role`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- -------------------------------------------------------------
-- 2. 学生认证申请表：一名用户可有多次被驳回记录，仅一条通过记录
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `student_auth`;
CREATE TABLE `student_auth` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       bigint      NOT NULL                COMMENT '申请用户ID',
  `student_no`    varchar(30) NOT NULL                COMMENT '学号',
  `real_name`     varchar(30) NOT NULL                COMMENT '真实姓名',
  `school`        varchar(100)         DEFAULT NULL   COMMENT '学校名称',
  `cert_img`      varchar(500) NOT NULL               COMMENT '学生证照片地址',
  `status`        tinyint     NOT NULL DEFAULT 0      COMMENT '审核状态:0待审核 1通过 2驳回',
  `reject_reason` varchar(200)         DEFAULT NULL   COMMENT '驳回原因',
  `auditor_id`    bigint               DEFAULT NULL   COMMENT '审核人ID',
  `audit_time`    datetime             DEFAULT NULL   COMMENT '审核时间',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_student_no` (`student_no`),
  KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生认证申请表';

-- -------------------------------------------------------------
-- 3. 初始化说明
--    后台账号（admin / service01）由应用启动时 DataInitializer 创建，
--    密码 123456 经 BCrypt 加密后写入，避免在 SQL 中硬编码密文。
--    首次启动后请立即登录管理端修改密码。
-- -------------------------------------------------------------

-- 验证：应返回 2 张表
-- SHOW TABLES;
