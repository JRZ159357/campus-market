// 文件路径: src/main/java/com/campus/market/service/HomeService.java
package com.campus.market.service;

import com.campus.market.vo.HomeIndexVO;

import java.util.List;

/**
 * 首页聚合业务。
 */
public interface HomeService {

    /**
     * 首页聚合：轮播图 + 分类 + 猜你喜欢 + 最新发布 + 热门搜索词。
     * 一次请求返回，避免弱网下多个串行请求叠加白屏时间。
     */
    HomeIndexVO index();

    /**
     * 热门搜索词。搜索页单独使用时调用，避免为了 10 个词拉整个首页数据。
     */
    List<String> hotKeywords();
}
