// 文件路径: src/main/java/com/campus/market/common/RedisKey.java
package com.campus.market.common;

/**
 * Redis Key 统一管理，避免散落的字符串拼接。
 */
public final class RedisKey {

    /** 登录 token 白名单：login:token:{userId} -> token，用于单端登录与强制下线 */
    public static final String LOGIN_TOKEN = "login:token:";

    /** 用户信息缓存：user:profile:{userId} -> JSON，TTL 30 分钟 */
    public static final String USER_PROFILE = "user:profile:";

    /** 微信 access_token 缓存 */
    public static final String WECHAT_ACCESS_TOKEN = "wechat:access_token";

    /** 商品浏览增量：Hash，field=productId，value=待回写次数，定时任务批量刷回 MySQL */
    public static final String PRODUCT_VIEW_DELTA = "product:view:delta";

    /** 热门商品缓存：String(JSON)，TTL 5 分钟 */
    public static final String PRODUCT_HOT = "product:hot";

    /**
     * 用户收藏的商品ID集合：Set，member=productId，TTL 7 天。
     * 判断「是否收藏」从一次 SQL 降为一次 SISMEMBER，O(1)；
     * 集合内固定写入哨兵 "0"，用于区分「已加载但为空」与「缓存不存在」。
     */
    public static final String FAVORITE_IDS = "favorite:ids:";

    /**
     * 聊天未读消息总数：String，value=未读条数，TTL 1 天。
     * <p>
     * 只缓存「总数」这一个标量，会话内的未读数仍以 chat_session.buyer_unread / seller_unread 为准。
     * 这样即使缓存丢失，重新回源一次聚合查询就能恢复，不会出现缓存与数据库长期不一致；
     * 发送消息、标记已读时同步删除该 key（写后失效），下次读取自然拿到新值。
     */
    public static final String CHAT_UNREAD = "chat:unread:";

    /** 系统通知未读数：String，value=未读条数，TTL 1 天，失效策略同 CHAT_UNREAD */
    public static final String NOTICE_UNREAD = "notice:unread:";

    /**
     * 仪表盘缓存前缀（模块 10）。
     * <p>
     * 仪表盘是「多源聚合」的典型场景：一次 summary 请求要合并用户、商品、订单、
     * 举报、聊天 5 张表的统计，而它的使用者是管理端首页——打开就要看，
     * 但没人关心它精确到秒。因此这里按「数据变化频率」分级设 TTL：
     * <ul>
     *   <li>{@link #DASHBOARD_SUMMARY}：5 分钟，指标卡，变动最频繁也最常被看；</li>
     *   <li>{@link #DASHBOARD_DISTRIBUTION}：5 分钟，分布图，与指标卡同步；</li>
     *   <li>{@link #DASHBOARD_TREND}：10 分钟，趋势图按天聚合，天然不需要更实时；</li>
     *   <li>{@link #DASHBOARD_RANKING}：30 分钟，排行榜变化最慢，压最长的 TTL。</li>
     * </ul>
     * 缓存内容是<b>全平台</b>的统计，不区分调用者，因此 key 里没有用户维度。
     */
    public static final String DASHBOARD_SUMMARY = "dashboard:summary:";

    public static final String DASHBOARD_DISTRIBUTION = "dashboard:distribution:";

    public static final String DASHBOARD_TREND = "dashboard:trend:";

    public static final String DASHBOARD_RANKING = "dashboard:ranking:";

    /**
     * 仪表盘「最近动态」缓存前缀。
     * <p>
     * 操作日志是追加写的，最近 20 条几乎每分钟都在变，但它只是一屏只读列表，
     * 短暂延迟完全可接受，所以给一个很短的 TTL（30 秒）而不是每次都查库。
     */
    public static final String DASHBOARD_RECENT = "dashboard:recent:";

    /**
     * 首页聚合结果缓存（模块 12）：String(JSON)，TTL 5 分钟。
     * <p>
     * 缓存的是<b>整个 HomeIndexVO</b>（轮播图 + 公告 + 金刚区 + 热门 + 新品），
     * 不是其中某几段：首页首屏本来就是一个请求换一屏内容，
     * 拆成多个 key 只会让「改一个配置要删几个 key」变成一道需要背下来的规则，
     * 而全量缓存让失效动作永远只有 DELETE 一次。
     * <p>
     * 只存一份、不带用户维度：首页内容对所有人一致（未登录也能看），
     * 个性化推荐是后续「AI 推荐」模块要解决的问题，不是这一层缓存该掺和的事。
     */
    public static final String HOME_INDEX = "home:index";

    private RedisKey() {
    }
}
