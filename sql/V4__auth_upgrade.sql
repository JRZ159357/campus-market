-- =============================================================
-- 校园二手交易平台 · V4 数据库脚本
-- 模块：权限体系升级（全员账号密码 + 客服只读 + 管理员全权）
-- 数据库：campus_market  字符集：utf8mb4
-- 说明：脚本可重复执行（先查 information_schema 再决定是否 ALTER）。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 新增注册方式字段
--    1 微信注册（open_id 登录，可后续绑定账号密码）
--    2 账号密码注册（username + password 登录）
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market'
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'register_type'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `register_type` tinyint NOT NULL DEFAULT 1 COMMENT ''注册方式:1微信 2账号密码'' AFTER `password`',
  'SELECT ''register_type 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 收紧账号字段长度并补充注释
--    账号固定 4-20 位字母数字，密码为 BCrypt 密文（60 位，预留 100）
-- -------------------------------------------------------------
ALTER TABLE `sys_user`
  MODIFY COLUMN `username` varchar(20)  DEFAULT NULL COMMENT '登录账号（4-20位字母数字），微信用户可为空',
  MODIFY COLUMN `password` varchar(100) DEFAULT NULL COMMENT '登录密码(BCrypt)，微信用户未绑定账号时为空';

-- -------------------------------------------------------------
-- 3. 存量后台账号（无 open_id、有 username）标记为账号密码注册
-- -------------------------------------------------------------
UPDATE `sys_user`
   SET `register_type` = 2
 WHERE `open_id` IS NULL
   AND `username` IS NOT NULL;

-- -------------------------------------------------------------
-- 4. 初始化说明
--    admin / service01 / service02 由应用启动时 DataInitializer 创建，
--    密码统一 123456（BCrypt 加密后入库），避免在 SQL 中硬编码密文。
--    role 取值：user / student / customer_service / admin
--    权限规则：customer_service 只读（仅 GET 放行），admin 全部放行。
-- -------------------------------------------------------------

-- 验证：register_type 列已存在，后台账号为 2
-- SELECT id, username, role, register_type FROM sys_user;
