// 文件路径: src/main/java/com/campus/market/controller/NoticeController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.NoticePublishDTO;
import com.campus.market.dto.NoticeQueryDTO;
import com.campus.market.service.NoticeService;
import com.campus.market.service.UnreadService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.NoticeBroadcastVO;
import com.campus.market.vo.NoticeVO;
import com.campus.market.vo.UnreadSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统通知接口。
 * <p>
 * 权限分层：
 * <ul>
 *   <li>个人通知（list / read / read-all / unread-count）：任何登录用户，只能操作自己的；</li>
 *   <li>公告管理（admin/page）：管理员与客服都能看，客服只读；</li>
 *   <li>发布与删除公告：仅管理员（客服调用会被拦截器与 @RequireRole 双重拒绝）。</li>
 * </ul>
 */
@Tag(name = "系统通知")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final UnreadService unreadService;

    @Operation(summary = "我的通知列表（未读优先）")
    @GetMapping("/list")
    public Result<IPage<NoticeVO>> list(NoticeQueryDTO query) {
        return Result.ok(noticeService.page(UserContext.getUserId(), query));
    }

    @Operation(summary = "通知详情（只能查看自己的通知）")
    @GetMapping("/{id}")
    public Result<NoticeVO> detail(@Parameter(description = "通知ID") @PathVariable Long id) {
        return Result.ok(noticeService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "标记单条通知已读")
    @PutMapping("/read/{id}")
    public Result<Void> read(@Parameter(description = "通知ID") @PathVariable Long id) {
        noticeService.read(UserContext.getUserId(), id);
        return Result.ok("已读", null);
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Integer> readAll() {
        return Result.ok("已全部标记为已读", noticeService.readAll(UserContext.getUserId()));
    }

    @Operation(summary = "未读数汇总（tabBar 角标）")
    @GetMapping("/unread-count")
    public Result<UnreadSummaryVO> unreadCount() {
        return Result.ok(unreadService.summary(UserContext.getUserId()));
    }

    @Operation(summary = "已发布公告列表（管理员 / 客服）")
    @GetMapping("/admin/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<NoticeBroadcastVO>> adminPage(NoticeQueryDTO query) {
        return Result.ok(noticeService.broadcastPage(query));
    }

    @Operation(summary = "发布系统公告（仅管理员）")
    @PostMapping("/admin/publish")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> publish(@RequestBody @Valid NoticePublishDTO dto) {
        noticeService.publish(dto);
        return Result.ok("公告已发布", null);
    }

    @Operation(summary = "删除公告（仅管理员，整组删除）")
    @DeleteMapping("/admin/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "公告分组代表ID") @PathVariable Long id) {
        noticeService.deleteBroadcast(id);
        return Result.ok("公告已删除", null);
    }
}
