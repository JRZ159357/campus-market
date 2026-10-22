// 文件路径: src/main/java/com/campus/market/controller/HomeController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.service.BannerService;
import com.campus.market.service.HomeService;
import com.campus.market.vo.BannerVO;
import com.campus.market.vo.HomeIndexVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页接口（全部免登录）。
 * <p>
 * {@code GET /api/home} 是模块 12 定下的正式地址，{@code /api/home/index}
 * 是模块 3 留下的别名。保留别名不是懒惰：小程序已经发过版本、
 * 接口文档里也印着 /index，改地址的唯一结果是线上旧版本白屏。
 * 一个 @GetMapping 里写两个路径，成本为零。
 */
@Tag(name = "首页")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final BannerService bannerService;

    @PublicApi
    @Operation(summary = "首页聚合数据：公告 + 轮播图 + 金刚区 + 热门推荐 + 新品推荐")
    @GetMapping({"", "/index"})
    public Result<HomeIndexVO> index() {
        return Result.ok(homeService.index());
    }

    @PublicApi
    @Operation(summary = "轮播图列表（启用中且在展示期内，按排序升序）")
    @GetMapping("/banner/list")
    public Result<List<BannerVO>> bannerList() {
        return Result.ok(bannerService.listActive());
    }

    @PublicApi
    @Operation(summary = "热门搜索词")
    @GetMapping("/hot-keywords")
    public Result<List<String>> hotKeywords() {
        return Result.ok(homeService.hotKeywords());
    }
}
