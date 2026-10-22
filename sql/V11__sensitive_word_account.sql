-- =============================================================
-- 模块 11 · 系统管理收尾（敏感词过滤 + 后台账号管理）
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/V11__sensitive_word_account.sql
--
-- 设计说明：
--   1) 敏感词表不存「命中记录」，只存词典本身。命中审计走已有的
--      sys_operation_log 与业务表上的标记字段，再建一张 hit 表会让
--      「拦截一次就要写一行」，而拦截恰恰是高频动作——为了留痕把
--      每一次被拦截的尝试都落库，表会在一周内涨到百万级，
--      查询价值却极低（没人会去翻「昨天有谁想发违禁品」）。
--   2) word 建唯一索引：批量导入时用 INSERT IGNORE 天然去重，
--      重复导入同一份词表不会产生脏数据，也不需要先 SELECT 再判断
--      （那是「查一次写一次」的两倍开销，并发下还会撞车）。
--   3) level 只有两级（1 警告 / 2 拦截）而不是用分值：分值需要
--      「累计到多少分才拦」的阈值配置，阈值一旦可配，运营就会去调它，
--      调完之后「为什么这条能发那条不能发」就说不清了。
--      两级语义清晰，审核员不需要培训。
--   4) type 用 tinyint 而不是 varchar：管理端按类型分 Tab 统计，
--      字符串分组会把「违禁品」「违禁物品」算成两类；
--      展示文案由后端 SensitiveWordType.label() 输出，改文案不用洗数据。
--   5) status 与「删除」是两件事：删除是这个词不再需要了，
--      禁用是「暂时不拦，但保留配置」。后者在活动期间很常用
--      （比如促销期临时允许出现「刷单」以外的营销词），
--      因此禁用词仍留在表里，只是不参与内存匹配。
--   6) product 增加 need_manual_review / sensitive_hit：
--      level=1 的词命中后商品照常发布，但必须让人看得见它被标记过。
--      只靠日志不够——审核员是按列表干活的，不会先去翻日志再决定审谁。
--      sensitive_hit 存命中的原始词，审核员一眼就知道该重点看什么。
--   7) 脚本可重复执行：CREATE TABLE IF NOT EXISTS / INSERT IGNORE /
--      列存在性判断后再 ALTER。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 敏感词表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word`        varchar(50)  NOT NULL                COMMENT '敏感词（归一化后比对，原文保留）',
  `type`        tinyint      NOT NULL DEFAULT 4      COMMENT '类型:1违禁品 2辱骂 3广告 4其他',
  `level`       tinyint      NOT NULL DEFAULT 1      COMMENT '等级:1警告(放行但标记) 2拦截(直接拒绝)',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '状态:0禁用 1启用',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 唯一索引承担两件事：批量导入去重、防止人工重复录入
  UNIQUE KEY `uk_word` (`word`),
  -- 管理端默认视图是「按类型 Tab + 按状态筛」，这个组合索引直接覆盖
  KEY `idx_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词表';

-- -------------------------------------------------------------
-- 2. 商品表增加「待人工复核」标记
--    先判断列是否存在，避免重复执行脚本时报 Duplicate column
-- -------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_market'
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'need_manual_review'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `product`
     ADD COLUMN `need_manual_review` tinyint NOT NULL DEFAULT 0 COMMENT ''待人工复核:0否 1是（命中警告级敏感词）'' AFTER `is_verified_seller`,
     ADD COLUMN `sensitive_hit` varchar(255) DEFAULT NULL COMMENT ''命中的敏感词，逗号分隔（用于审核时提示）'' AFTER `need_manual_review`,
     ADD KEY `idx_manual_review` (`need_manual_review`, `status`)',
  'SELECT ''product 表已包含 need_manual_review，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. 预置敏感词
--    INSERT IGNORE 依赖 uk_word：重复执行不会报错也不会重复插入。
--    等级分配原则——「交易违禁」与「人身攻击」直接拦（2），
--    因为这两类一旦发出去就是平台责任；「引流广告」先警告（1），
--    因为「加微信」在二手交易里有时是真的需要线下验货，
--    一刀切拦截会误伤，交给人工看一眼更稳。
-- -------------------------------------------------------------
INSERT IGNORE INTO `sensitive_word` (`word`, `type`, `level`, `status`) VALUES
  -- 违禁品类（type=1）：一律拦截
  ('走私',     1, 2, 1),
  ('枪支',     1, 2, 1),
  ('毒品',     1, 2, 1),
  ('假币',     1, 2, 1),
  ('窃听器',   1, 2, 1),
  ('管制刀具', 1, 2, 1),
  -- 辱骂类（type=2）：一律拦截
  ('傻逼',     2, 2, 1),
  ('脑残',     2, 2, 1),
  ('废物',     2, 2, 1),
  ('垃圾人',   2, 2, 1),
  ('去死',     2, 2, 1),
  -- 广告类（type=3）：先警告后人工复核
  ('加微信',   3, 1, 1),
  ('刷单',     3, 1, 1),
  ('代刷',     3, 1, 1),
  ('兼职日入', 3, 1, 1),
  ('私聊下单', 3, 1, 1),
  -- 其他类（type=4）：欺诈相关拦截，其余警告
  ('骗子',     4, 1, 1),
  ('跑路',     4, 1, 1),
  ('套现',     4, 2, 1),
  ('代开发票', 4, 2, 1);

-- -------------------------------------------------------------
-- 4. 校验
-- -------------------------------------------------------------
SELECT `type`, `level`, COUNT(*) AS cnt
FROM `sensitive_word`
GROUP BY `type`, `level`
ORDER BY `type`, `level`;
