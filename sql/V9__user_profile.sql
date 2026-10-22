-- =============================================================
-- 模块 8 · 个人中心与卖家主页
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/V9__user_profile.sql
--
-- 设计说明：
--   1) 本模块不新增核心业务表，全部是前 7 个模块数据的聚合与展示。
--      唯一需要的结构变更是给 sys_user 补两个「个人资料」字段：
--      gender（性别）与 intro（个人简介）。
--   2) gender 用 tinyint 而不是字符串枚举：
--      存 0/1/2，判空与排序都走整数，扩展第四种取值时也不必改列类型。
--   3) intro 长度定 200 字：个人简介属于「一句话自我介绍」，
--      太长会稀释商品信息，数据库层就用 varchar(200) 卡住，
--      应用层再按 100 字截断，前端与服务端口径一致。
--   4) 注销账号不需要新表：sys_user 已有 deleted 逻辑删除列，
--      商品下架复用 product.status（1 已上架 → 2 已下架），
--      与「卖家主动下架」走同一条路径，前端展示逻辑完全一致。
--   5) 管理端要按「学号」搜用户：student_auth.student_no 已存在，
--      直接子查询关联即可，不做冗余。
--   6) 脚本可重复执行：新增字段先查 information_schema。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. sys_user 增加性别与个人简介
--    为什么放在这里而不是新建 user_profile 表：
--    昵称、头像、性别、简介都是「用户自身的低频附加属性」，
--    与 sys_user 是一对一关系，拆表只会让每次读用户信息多一次 JOIN。
--    真正需要拆表的是「一对多、会被独立检索」的东西（如订单、评价）。
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'gender'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `gender` tinyint NOT NULL DEFAULT 0 COMMENT ''性别:0保密 1男 2女'' AFTER `avatar`',
  'SELECT ''gender 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'intro'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `intro` varchar(200) DEFAULT NULL COMMENT ''个人简介'' AFTER `gender`',
  'SELECT ''intro 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 管理端用户列表按「注册时间」与「信誉分区间」筛选
--    补两个索引：注册时间几乎按插入顺序递增（等价于主键顺序，
--    范围扫描本可以走主键，但一旦有别的主键排序方式就会退化）；
--    信誉分区间筛选属于低频管理端查询，不建索引避免拖慢写入。
-- -------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_create_time'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE `sys_user` ADD INDEX `idx_user_create_time` (`create_time`)',
  'SELECT ''idx_user_create_time 已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
