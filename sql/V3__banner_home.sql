-- =============================================================
-- 校园二手交易平台 · V3 数据库脚本
-- 模块：首页与商品浏览
-- 依赖：V1（sys_user）、V2（category / product）
-- 说明：
--   1) banner 采用与项目一致的时间字段命名 create_time / update_time，
--      逻辑删除 deleted，不建物理外键；
--   2) start_time / end_time 可空，为空表示长期有效，用于定时上下架；
--   3) 为首页与列表新增两个组合索引：
--      idx_status_id            —— 游标分页（status + id 倒序）走索引，避免深分页 offset；
--      idx_status_view_fav      —— 热门商品按浏览量+收藏量加权排序；
--   4) 不预置轮播图数据：图片必须由管理员在「首页配置」上传真实素材，
--      小程序端对空数据做了渐变降级横幅，不会出现空白块。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 首页轮播图表
--    link_type: 1商品详情 2分类页 3外部链接
--    link_value: 商品ID / 分类ID / 完整URL
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`       varchar(80)  NOT NULL                COMMENT '标题',
  `image_url`   varchar(500) NOT NULL                COMMENT '图片地址',
  `link_type`   tinyint      NOT NULL DEFAULT 1      COMMENT '跳转类型:1商品详情 2分类页 3外部链接',
  `link_value`  varchar(500)          DEFAULT NULL   COMMENT '跳转值：商品ID/分类ID/URL',
  `sort`        int          NOT NULL DEFAULT 1      COMMENT '排序值，越小越靠前',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '状态:0禁用 1启用',
  `start_time`  datetime              DEFAULT NULL   COMMENT '生效时间，为空表示立即生效',
  `end_time`    datetime              DEFAULT NULL   COMMENT '失效时间，为空表示长期有效',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`),
  KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='首页轮播图表';

-- -------------------------------------------------------------
-- 2. 浏览性能索引
--    MySQL 8 不支持 ADD INDEX IF NOT EXISTS，这里先查 information_schema 再执行，
--    保证脚本可重复执行。
-- -------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'product'
                     AND index_name = 'idx_status_id');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `product` ADD INDEX `idx_status_id` (`status`, `id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'product'
                     AND index_name = 'idx_status_view_fav');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `product` ADD INDEX `idx_status_view_fav` (`status`, `view_count`, `favorite_count`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证：
-- SHOW TABLES;
-- SHOW INDEX FROM product WHERE Key_name IN ('idx_status_id', 'idx_status_view_fav');
-- SELECT COUNT(*) FROM banner;
