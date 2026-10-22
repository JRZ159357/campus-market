// 文件路径: src/main/java/com/campus/market/common/HomeConfigKey.java
package com.campus.market.common;

/**
 * 首页配置的键名与取值约束（模块 12）。
 * <p>
 * 把「表里的字符串」与「代码里的常量」绑定在一处，原因和 {@link OpLogModule} 一样：
 * 这些 key 会在 Service、SQL 预置数据、前端联调三处出现，
 * 任何一处手写成 "noticeText" 都会变成「写了但读不到」的静默故障——
 * 页面上看到的是默认值，而不是报错。
 * <p>
 * 表结构是 KV，但<b>接口不是 KV</b>：给前端的仍然是结构化对象，
 * 由 Service 负责铺平/收拢。让前端拼 key 等于把表结构暴露成接口契约，
 * 以后想加一项配置要同时改前端和数据库。
 */
public final class HomeConfigKey {

    /** 公告文字，空字符串表示不展示 */
    public static final String NOTICE_TEXT = "notice_text";

    /** 公告点击跳转地址，可为空 */
    public static final String NOTICE_LINK = "notice_link";

    /** 公告是否展示：0 否 1 是 */
    public static final String NOTICE_ENABLED = "notice_enabled";

    /** 热门推荐模式：manual 手动选品 / auto 按热度自动 */
    public static final String HOT_MODE = "hot_mode";

    /** 手动模式选中的商品ID，JSON 数组，顺序即展示顺序 */
    public static final String HOT_PRODUCT_IDS = "hot_product_ids";

    /** 自动模式取前 N 件 */
    public static final String HOT_COUNT = "hot_count";

    /** 新品推荐取最新 N 件 */
    public static final String NEW_COUNT = "new_count";

    // ==================== 取值 ====================

    /** 热门推荐：手动选品 */
    public static final String MODE_MANUAL = "manual";

    /** 热门推荐：按热度自动 */
    public static final String MODE_AUTO = "auto";

    // ==================== 约束 ====================

    /** 公告最多 100 字。小程序公告栏是一行滚动，再长就只剩滚动条在动，没人读得完 */
    public static final int NOTICE_TEXT_MAX = 100;

    /** 手动选品上限 20 件。首页不是商品列表页，堆太多反而没人往下滑 */
    public static final int HOT_PICK_MAX = 20;

    /** 自动模式/新品推荐的数量上限 */
    public static final int RECOMMEND_COUNT_MAX = 20;

    /** 自动模式/新品推荐的默认数量 */
    public static final int RECOMMEND_COUNT_DEFAULT = 10;

    private HomeConfigKey() {
    }

    /**
     * 配置项缺行时的默认值。
     * <p>
     * 默认值必须写在代码里而不是只靠 SQL 预置：
     * 表被清空、新环境忘了跑脚本、运维误删一行，首页都要照常能打开。
     * 首页打不开是最贵的事故，它的严重程度远高于「公告文案不对」。
     */
    public static String defaultValue(String key) {
        return switch (key) {
            case NOTICE_TEXT -> "校园二手交易平台正式上线，认证学生发布更放心";
            case NOTICE_LINK -> "";
            case NOTICE_ENABLED -> "1";
            case HOT_MODE -> MODE_AUTO;
            case HOT_PRODUCT_IDS -> "[]";
            case HOT_COUNT -> String.valueOf(RECOMMEND_COUNT_DEFAULT);
            case NEW_COUNT -> String.valueOf(RECOMMEND_COUNT_DEFAULT);
            default -> null;
        };
    }
}
