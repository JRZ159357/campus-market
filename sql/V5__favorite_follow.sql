-- =============================================================
-- 校园二手交易平台 · V5 数据库脚本
-- 模块：收藏与关注
-- 依赖：V1（sys_user）、V2（product）
-- 说明：
--   1) favorite / follow 均采用「联合唯一索引 + 删除即物理删除」的写法：
--      收藏与关注是用户行为记录，取消后没有留痕价值，物理删除可让
--      唯一索引天然承担幂等与防重复的职责，也避免逻辑删除与唯一索引冲突
--      （deleted=0/1 时同一组合无法再次写入）。
--   2) 游标分页依赖 (user_id, id) / (follower_id, id) / (following_id, id)：
--      id 自增等价于行为发生顺序，`id < cursor ORDER BY id DESC` 可走索引，
--      避免深分页 OFFSET 扫描。
--   3) 计数冗余：product.favorite_count 已存在；sys_user 新增
--      favorite_count（我收藏的商品数）、follow_count（我关注的人数）、
--      fans_count（关注我的人数）。读多写少，用冗余换取列表页免 COUNT。
--   4) 脚本可重复执行：新增字段先查 information_schema，建表用 DROP IF EXISTS。
--      重复执行会清空收藏/关注数据，仅用于开发环境重建。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. sys_user 增加三个冗余计数
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'favorite_count'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `favorite_count` int NOT NULL DEFAULT 0 COMMENT ''我收藏的商品数（冗余计数）'' AFTER `publish_limit`',
  'SELECT ''favorite_count 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'follow_count'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `follow_count` int NOT NULL DEFAULT 0 COMMENT ''我关注的人数（冗余计数）'' AFTER `favorite_count`',
  'SELECT ''follow_count 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'fans_count'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `fans_count` int NOT NULL DEFAULT 0 COMMENT ''关注我的人数（粉丝数，冗余计数）'' AFTER `follow_count`',
  'SELECT ''fans_count 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 收藏表
--    uk_user_product：同一用户对同一商品只能有一条记录，
--    既是业务约束，也让并发重复点击只会产生一次写入。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键，自增顺序即收藏先后顺序（游标分页依据）',
  `user_id`     bigint   NOT NULL                COMMENT '收藏用户ID',
  `product_id`  bigint   NOT NULL                COMMENT '被收藏商品ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_id_id` (`user_id`, `id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收藏表';

-- -------------------------------------------------------------
-- 3. 关注表
--    uk_follower_following：防止重复关注；
--    chk_not_self：数据库层兜底禁止自关注（服务层同样校验，返回友好提示）。
--    idx_following_id 用于「粉丝列表」按被关注者反查。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `follow`;
CREATE TABLE `follow` (
  `id`           bigint   NOT NULL AUTO_INCREMENT COMMENT '主键，自增顺序即关注先后顺序（游标分页依据）',
  `follower_id`  bigint   NOT NULL                COMMENT '关注者ID',
  `following_id` bigint   NOT NULL                COMMENT '被关注者ID（卖家）',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
  KEY `idx_follower_id` (`follower_id`, `id`),
  KEY `idx_following_id` (`following_id`, `id`),
  CONSTRAINT `chk_follow_not_self` CHECK (`follower_id` <> `following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='关注表';

-- -------------------------------------------------------------
-- 4. 计数归零
--    两张行为表已重建，冗余计数必须同步清零，否则历史计数会凭空存在。
--    （开发环境脚本，生产环境应改为按表重算）
-- -------------------------------------------------------------
UPDATE `sys_user` SET `favorite_count` = 0, `follow_count` = 0, `fans_count` = 0;
UPDATE `product` SET `favorite_count` = 0;

-- 验证：应返回 favorite / follow 两张表
-- SHOW TABLES LIKE '%favorite%';
-- SHOW TABLES LIKE 'follow';
-- SELECT id, nickname, favorite_count, follow_count, fans_count FROM sys_user;
