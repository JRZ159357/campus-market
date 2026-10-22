-- =============================================================
-- 校园二手交易平台 · V2 数据库脚本
-- 模块：商品分类与商品发布
-- 依赖：V1__user_auth.sql（sys_user）
-- 说明：
--   1) 全部表采用逻辑外键，不建物理外键约束；
--   2) 商品状态：0待审核 1已上架 2已下架 3已售出 4违规下架；
--   3) product.is_verified_seller 为冗余字段，用于"认证学生优先推荐"排序，
--      发布时写入，避免列表查询 JOIN sys_user 后再排序导致索引失效。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 商品分类表
--    icon 兼容两种存法：图片地址（/uploads/... 或 http）或短文本/emoji，
--    便于小程序分类宫格在没有图标资源时直接渲染。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        varchar(30) NOT NULL                COMMENT '分类名称',
  `icon`        varchar(500)         DEFAULT NULL   COMMENT '图标：图片地址或emoji文本',
  `sort`        int         NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
  `status`      tinyint     NOT NULL DEFAULT 1      COMMENT '状态:0禁用 1启用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint     NOT NULL DEFAULT 0      COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品分类表';

-- 预置分类（与需求一致，sort 按展示顺序）
INSERT INTO `category` (`name`, `icon`, `sort`, `status`) VALUES
('数码电子', '📱', 1, 1),
('教材书籍', '📚', 2, 1),
('生活用品', '🧴', 3, 1),
('服饰鞋包', '👕', 4, 1),
('运动户外', '🏀', 5, 1),
('交通工具', '🚲', 6, 1),
('其他',     '📦', 7, 1);

-- -------------------------------------------------------------
-- 2. 商品表
--    audit_reason 记录审核驳回原因或强制下架原因，卖家端需要展示
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seller_id`          bigint        NOT NULL                COMMENT '卖家用户ID',
  `category_id`        bigint        NOT NULL                COMMENT '分类ID',
  `title`              varchar(60)   NOT NULL                COMMENT '标题，最多30字',
  `description`        varchar(1000)          DEFAULT NULL   COMMENT '商品描述，最多500字',
  `price`              decimal(10,2) NOT NULL                COMMENT '售价',
  `original_price`     decimal(10,2)          DEFAULT NULL   COMMENT '原价，选填',
  `status`             tinyint       NOT NULL DEFAULT 0      COMMENT '状态:0待审核 1已上架 2已下架 3已售出 4违规下架',
  `view_count`         int           NOT NULL DEFAULT 0      COMMENT '浏览数',
  `favorite_count`     int           NOT NULL DEFAULT 0      COMMENT '收藏数',
  `is_verified_seller` tinyint       NOT NULL DEFAULT 0      COMMENT '卖家是否认证学生:0否 1是（冗余，优先推荐用）',
  `audit_reason`       varchar(200)           DEFAULT NULL   COMMENT '审核驳回原因 / 强制下架原因',
  `auditor_id`         bigint                 DEFAULT NULL   COMMENT '审核人ID',
  `audit_time`         datetime               DEFAULT NULL   COMMENT '审核时间',
  `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            tinyint       NOT NULL DEFAULT 0      COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_status_verified_create` (`status`, `is_verified_seller`, `create_time`),
  KEY `idx_category_status` (`category_id`, `status`),
  KEY `idx_seller_status` (`seller_id`, `status`),
  KEY `idx_title` (`title`),
  KEY `idx_price` (`price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品表';

-- -------------------------------------------------------------
-- 3. 商品图片表：一个商品最多 9 张，sort=1 为主图
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `product_image`;
CREATE TABLE `product_image` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`  bigint       NOT NULL                COMMENT '商品ID',
  `image_url`   varchar(500) NOT NULL                COMMENT '图片地址',
  `sort`        int          NOT NULL DEFAULT 1      COMMENT '排序，1为主图',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除:0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_product_sort` (`product_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品图片表';

-- -------------------------------------------------------------
-- 4. 发布上限调整
--    模块1 按"默认10件"实现；本模块需求明确为"最多同时上架20件"，
--    这里同步存量认证学生数据，新认证用户由代码写入 20。
-- -------------------------------------------------------------
UPDATE `sys_user` SET `publish_limit` = 20
WHERE `role` = 'student' AND `publish_limit` = 10;

-- 验证：
-- SHOW TABLES;
-- SELECT id, name, icon, sort, status FROM category ORDER BY sort;
