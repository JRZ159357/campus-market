-- =============================================================
-- 模块 7 · 评价与信誉分
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/V8__review_credit.sql
--
-- 设计说明：
--   1) review 一张表同时承载「买家评卖家」与「卖家评买家」两个方向，
--      靠 review_type 区分，避免两张结构 90% 相同的表；
--      uk_order_type(order_id, review_type) 用联合唯一索引在数据库层
--      兜住「同一订单同一方向只能评一次」，并发重复提交也只会成功一条。
--   2) 各维度星级列允许为 NULL：买家评卖家只有 desc/ship/attitude，
--      卖家评买家只有 pay，用 NULL 表达「该方向没有这个维度」，
--      比填 0 更准确——0 会让 AVG() 把缺失维度算成 0 分。
--   3) stars 是综合星级（各维度平均四舍五入），冗余存储：
--      列表页与信誉分计算都要用它，实时算 AVG 既费 CPU 又无法建索引。
--   4) credit_log 记录每一次信誉分变动（含管理员手动调整、评价加减分、
--      举报扣分、评价被删除的审计痕迹），既是「调整历史」的数据源，
--      也让信誉分可追溯——只改 sys_user.credit_score 而不留痕，
--      一旦用户质疑分数就无法解释。
--   5) 脚本可重复执行：建表用 CREATE TABLE IF NOT EXISTS，
--      新增字段先查 information_schema。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. trade_order 增加双向评价标记
--    为什么冗余在订单上：订单列表 / 待评价列表按「未评价」筛选时，
--    如果每次都去 review 表 LEFT JOIN 判断，订单表变大后会拖慢列表；
--    评价是不可逆的一次性动作，标记位不会频繁变化。
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'buyer_reviewed'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `buyer_reviewed` tinyint NOT NULL DEFAULT 0 COMMENT ''买家是否已评价:0未评 1已评'' AFTER `refund_from_status`',
  'SELECT ''buyer_reviewed 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market' AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'seller_reviewed'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `seller_reviewed` tinyint NOT NULL DEFAULT 0 COMMENT ''卖家是否已评价:0未评 1已评'' AFTER `buyer_reviewed`',
  'SELECT ''seller_reviewed 字段已存在，跳过新增'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 评价表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `review` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`        bigint       NOT NULL                COMMENT '订单ID',
  `order_no`        varchar(32)  NOT NULL                COMMENT '订单号（冗余，详情页免 JOIN）',
  `product_id`      bigint       NOT NULL                COMMENT '商品ID（商品评价列表按此查询）',
  `reviewer_id`     bigint       NOT NULL                COMMENT '评价人ID',
  `reviewee_id`     bigint       NOT NULL                COMMENT '被评价人ID（信誉分加到这个用户身上）',
  `review_type`     tinyint      NOT NULL                COMMENT '评价方向:1买家评卖家 2卖家评买家',
  `stars`           tinyint      NOT NULL                COMMENT '综合星级1-5，各维度平均四舍五入',
  `desc_stars`      tinyint               DEFAULT NULL   COMMENT '描述相符1-5，仅买家评卖家',
  `ship_stars`      tinyint               DEFAULT NULL   COMMENT '发货速度1-5，仅买家评卖家',
  `attitude_stars`  tinyint               DEFAULT NULL   COMMENT '沟通态度1-5，两个方向都有',
  `pay_stars`       tinyint               DEFAULT NULL   COMMENT '付款速度1-5，仅卖家评买家',
  `content`         varchar(500)          DEFAULT NULL   COMMENT '文字评价，选填',
  `images`          varchar(1000)         DEFAULT NULL   COMMENT '图片JSON数组，最多3张',
  `is_anonymous`    tinyint      NOT NULL DEFAULT 0      COMMENT '是否匿名:0实名 1匿名',
  `reply_content`   varchar(500)          DEFAULT NULL   COMMENT '卖家回复内容（仅被评价方为卖家时可回复）',
  `reply_time`      datetime              DEFAULT NULL   COMMENT '卖家回复时间',
  `follow_content`  varchar(500)          DEFAULT NULL   COMMENT '追评内容',
  `follow_images`   varchar(1000)         DEFAULT NULL   COMMENT '追评图片JSON数组',
  `follow_time`     datetime              DEFAULT NULL   COMMENT '追评时间',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除:0正常 1已删除（违规评价）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_type` (`order_id`, `review_type`),
  KEY `idx_reviewee_time` (`reviewee_id`, `create_time`),
  KEY `idx_reviewer_time` (`reviewer_id`, `create_time`),
  KEY `idx_product_time` (`product_id`, `create_time`),
  KEY `idx_stars_time` (`stars`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单评价表';

-- -------------------------------------------------------------
-- 3. 信誉分变动流水
--    每次 score 变化必须写一条，用 before/after 两个快照值，
--    这样即使后续算法调整，历史记录也能解释当时的加减。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `credit_log` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       bigint      NOT NULL                COMMENT '被调整的用户ID',
  `change_score`  int         NOT NULL                COMMENT '本次变动值，正加负减',
  `before_score`  int         NOT NULL                COMMENT '变动前分数',
  `after_score`   int         NOT NULL                COMMENT '变动后分数（已按 0-150 截断）',
  `type`          tinyint     NOT NULL                COMMENT '类型:1收到评价 2举报违规扣分 3管理员调整 4评价被删除(审计)',
  `reason`        varchar(200)         DEFAULT NULL   COMMENT '变动原因',
  `related_id`    bigint               DEFAULT NULL   COMMENT '关联业务ID（评价ID / 举报ID）',
  `operator_id`   bigint               DEFAULT NULL   COMMENT '操作人ID，系统自动为NULL',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_type_time` (`type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='信誉分变动流水';

-- -------------------------------------------------------------
-- 4. 一致性兜底：把历史已完成订单的评价标记按已有评价回填
--    （本脚本首次执行时 review 为空，等同于初始化；
--     重复执行也不会把已删除评价的标记错误回填，因为按 deleted=0 统计）
-- -------------------------------------------------------------
UPDATE `trade_order` o
SET o.`buyer_reviewed` = 1
WHERE EXISTS (
  SELECT 1 FROM `review` r
  WHERE r.`order_id` = o.`id` AND r.`review_type` = 1 AND r.`deleted` = 0
);

UPDATE `trade_order` o
SET o.`seller_reviewed` = 1
WHERE EXISTS (
  SELECT 1 FROM `review` r
  WHERE r.`order_id` = o.`id` AND r.`review_type` = 2 AND r.`deleted` = 0
);
