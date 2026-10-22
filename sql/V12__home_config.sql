-- =============================================================
-- 校园二手交易平台 · V12 数据库脚本
-- 模块：首页推荐位配置（模块12，最后一个功能模块）
-- 依赖：V1（sys_user）、V2（category）、V3（banner / home）
-- 说明：
--   1) banner 是模块3就建好的表，本模块只补两个字段：
--      position（展示位置，为「分类页顶部轮播」预留）与 link_type=4（无跳转）；
--   2) category 补 is_home_show（是否进首页金刚区），排序复用已有 sort 字段，
--      不另建一张「金刚区表」——金刚区是分类的一个视图属性，不是独立实体，
--      拆表会导致分类改名/删除时要同步两处，反而更容易不一致；
--   3) home_config 用 key-value 结构：首页可配置项还会继续加（比如后续的
--      「活动倒计时」「客服二维码」），每加一项就改表结构是不可接受的，
--      KV 让新增配置只需加一个 key + 一条默认值；
--   4) 全脚本可重复执行（列/索引先查 information_schema，数据用 NOT EXISTS / INSERT IGNORE），
--      方便反复演示与重建环境。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. banner 增加 position（展示位置）
--    当前只有「首页轮播」一个位置，加这一列不是过度设计：
--    轮播图天然会出现在多个页面（首页、分类页顶部、活动页），
--    预留位置维度后，将来加位置只需在常量里加一个值，
--    而不必再给 banner 动一次表结构 + 改一次所有查询。
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE table_schema = DATABASE() AND table_name = 'banner'
                     AND column_name = 'position');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `banner` ADD COLUMN `position` tinyint NOT NULL DEFAULT 1 COMMENT ''展示位置:1首页轮播 2分类页顶部(预留)'' AFTER `link_value`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- link_type 增加取值 4（无跳转）：纯展示型海报不需要跳转，
-- 原先只有 1/2/3，运营想放一张纯品牌图只能挂一个假链接。
ALTER TABLE `banner`
  MODIFY COLUMN `link_type` tinyint NOT NULL DEFAULT 1
  COMMENT '跳转类型:1商品详情 2分类页 3外部链接 4无跳转';

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE table_schema = DATABASE() AND table_name = 'banner'
                     AND index_name = 'idx_position_status_sort');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `banner` ADD INDEX `idx_position_status_sort` (`position`, `status`, `sort`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. category 增加 is_home_show（是否显示在首页金刚区）
--    默认 1：新分类默认就上首页，符合「分类刚建出来就是要用的」的直觉；
--    想下掉某个分类时管理员在金刚区配置里关掉，而不是删分类。
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE table_schema = DATABASE() AND table_name = 'category'
                     AND column_name = 'is_home_show');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `category` ADD COLUMN `is_home_show` tinyint NOT NULL DEFAULT 1 COMMENT ''是否显示在首页金刚区:0否 1是'' AFTER `sort`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE table_schema = DATABASE() AND table_name = 'category'
                     AND index_name = 'idx_home_show');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `category` ADD INDEX `idx_home_show` (`is_home_show`, `sort`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. 首页配置表（key-value）
--    config_value 统一存字符串：纯文本直接存，结构化数据存 JSON。
--    不在数据库层存 JSON 类型，是因为 MySQL 的 JSON 列在
--    MyBatis-Plus + String 映射下要额外处理，而这里的配置项都很小，
--    解析成本远低于「换一种类型」带来的适配成本。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `home_config` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key`   varchar(64)  NOT NULL                COMMENT '配置键',
  `config_value` varchar(2000)         DEFAULT NULL   COMMENT '配置值：纯文本或 JSON 字符串',
  `remark`       varchar(200)          DEFAULT NULL   COMMENT '配置说明（给运维看，不参与业务）',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='首页配置表';

-- -------------------------------------------------------------
-- 4. 默认配置
--    INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE：
--    重跑脚本时不能把管理员已经改过的公告/选品覆盖回默认值。
-- -------------------------------------------------------------
INSERT IGNORE INTO `home_config` (`config_key`, `config_value`, `remark`) VALUES
  ('notice_text',     '校园二手交易平台正式上线，认证学生发布更放心', '首页公告文字，空则不显示'),
  ('notice_link',     NULL,                                     '公告点击跳转地址，可空'),
  ('notice_enabled',  '1',                                      '公告是否显示:0否 1是'),
  ('hot_mode',        'auto',                                   '热门推荐模式:manual手动选品 auto按热度'),
  ('hot_product_ids', '[]',                                     '手动模式选中的商品ID（JSON数组，有序，最多20个）'),
  ('hot_count',       '10',                                     '自动模式取前N件（1-20）'),
  ('new_count',       '10',                                     '新品推荐取最新N件（1-20）');

-- -------------------------------------------------------------
-- 5. 预置轮播图（3 张）
--    图片文件随仓库放在 backend/uploads/2026/10/ 下，
--    是纯渐变占位图：真实素材要运营自己上传，这里只保证
--    「全新环境启动后首页不是空白的」，不伪造业务内容。
--    NOT EXISTS 保证重复执行不会插出三份。
-- -------------------------------------------------------------
INSERT INTO `banner` (`title`, `image_url`, `link_type`, `link_value`, `position`, `sort`, `status`, `start_time`, `end_time`)
SELECT '开学季 · 二手教材专区', '/uploads/2026/10/seed-banner-1.png', 2,
       (SELECT `id` FROM `category` WHERE `name` = '教材书籍' LIMIT 1),
       1, 1, 1, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `banner` WHERE `title` = '开学季 · 二手教材专区' AND `deleted` = 0);

INSERT INTO `banner` (`title`, `image_url`, `link_type`, `link_value`, `position`, `sort`, `status`, `start_time`, `end_time`)
SELECT '数码好物 · 闲置出清', '/uploads/2026/10/seed-banner-2.png', 2,
       (SELECT `id` FROM `category` WHERE `name` = '数码电子' LIMIT 1),
       1, 2, 1, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `banner` WHERE `title` = '数码好物 · 闲置出清' AND `deleted` = 0);

INSERT INTO `banner` (`title`, `image_url`, `link_type`, `link_value`, `position`, `sort`, `status`, `start_time`, `end_time`)
SELECT '认证学生发布 · 平台审核上架', '/uploads/2026/10/seed-banner-3.png', 4, NULL,
       1, 3, 1, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `banner` WHERE `title` = '认证学生发布 · 平台审核上架' AND `deleted` = 0);

-- -------------------------------------------------------------
-- 6. 金刚区：存量分类默认全部上首页（新列默认值已覆盖新增行，
--    这里兜底把历史行也置 1，避免老数据被默认值漏掉）
-- -------------------------------------------------------------
UPDATE `category` SET `is_home_show` = 1 WHERE `is_home_show` IS NULL;

-- 验证：
-- SHOW COLUMNS FROM banner LIKE 'position';
-- SHOW COLUMNS FROM category LIKE 'is_home_show';
-- SELECT config_key, config_value FROM home_config ORDER BY id;
-- SELECT id, title, link_type, sort, status FROM banner WHERE deleted = 0 ORDER BY sort;
