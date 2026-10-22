// 文件路径: src/main/java/com/campus/market/controller/ChatController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.ChatQueryDTO;
import com.campus.market.dto.ChatSendDTO;
import com.campus.market.dto.ChatSessionCreateDTO;
import com.campus.market.service.ChatService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.ChatMessageVO;
import com.campus.market.vo.ChatSessionDetailVO;
import com.campus.market.vo.ChatSessionVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天接口。
 * <p>
 * 实时性靠 WebSocket（{@code /ws/chat}），但「拉历史消息」「标记已读」「未读数」仍然走 HTTP：
 * 长连接只负责「有新消息时推给你」，其余都是请求-响应语义，用 REST 更简单也更可靠。
 * <p>
 * 客服工作台的两个接口是只读的（GET 天然满足客服只读约束），
 * 客服不能主动发消息，因此这里没有任何客服写接口。
 */
@Tag(name = "聊天")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "我的会话列表（按最后消息时间倒序）")
    @GetMapping("/session/list")
    public Result<IPage<ChatSessionVO>> sessionList(ChatQueryDTO query) {
        return Result.ok(chatService.mySessions(UserContext.getUserId(), query));
    }

    @Operation(summary = "会话详情（含最近50条消息）")
    @GetMapping("/session/{id}")
    public Result<ChatSessionDetailVO> sessionDetail(@Parameter(description = "会话ID") @PathVariable Long id) {
        return Result.ok(chatService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "创建或获取会话（同一对买卖家同一商品只会有一个会话）")
    @PostMapping("/session")
    public Result<ChatSessionVO> createSession(@RequestBody @Valid ChatSessionCreateDTO dto) {
        return Result.ok("会话已创建", chatService.createOrGet(UserContext.getUserId(), dto));
    }

    @Operation(summary = "删除会话（仅从自己列表移除，对方仍可见）")
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@Parameter(description = "会话ID") @PathVariable Long id) {
        chatService.deleteSession(UserContext.getUserId(), id);
        return Result.ok("会话已删除", null);
    }

    @Operation(summary = "某商品下我作为卖家收到的咨询会话（我的发布 → 查看咨询）")
    @GetMapping("/session/by-product/{productId}")
    public Result<List<ChatSessionVO>> sessionsByProduct(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        return Result.ok(chatService.listByProduct(UserContext.getUserId(), productId));
    }

    @Operation(summary = "历史消息（游标分页，倒序返回）")
    @GetMapping("/message/{sessionId}")
    public Result<List<ChatMessageVO>> history(@Parameter(description = "会话ID") @PathVariable Long sessionId,
                                               @Parameter(description = "游标：上一页最小消息ID")
                                               @RequestParam(required = false) Long cursor,
                                               @Parameter(description = "每页条数，默认20")
                                               @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(chatService.history(UserContext.getUserId(), sessionId, cursor, size));
    }

    @Operation(summary = "发送消息（同时通过 WebSocket 推送给对方）")
    @PostMapping("/message")
    public Result<ChatMessageVO> sendMessage(@RequestBody @Valid ChatSendDTO dto) {
        return Result.ok("已发送", chatService.sendMessage(UserContext.getUserId(), dto));
    }

    @Operation(summary = "标记会话已读")
    @PutMapping("/message/read/{sessionId}")
    public Result<Void> markRead(@Parameter(description = "会话ID") @PathVariable Long sessionId) {
        chatService.markRead(UserContext.getUserId(), sessionId);
        return Result.ok("已读", null);
    }

    @Operation(summary = "未读数汇总（tabBar 角标）")
    @GetMapping("/unread")
    public Result<UnreadSummaryVO> unread() {
        return Result.ok(chatService.unreadSummary(UserContext.getUserId()));
    }

    @Operation(summary = "客服工作台：全部会话列表（只读）")
    @GetMapping("/admin/session/list")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<ChatSessionVO>> adminSessionList(ChatQueryDTO query) {
        return Result.ok(chatService.adminSessions(query));
    }

    @Operation(summary = "客服工作台：查看会话聊天记录（只读）")
    @GetMapping("/admin/message/{sessionId}")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<List<ChatMessageVO>> adminMessages(
            @Parameter(description = "会话ID") @PathVariable Long sessionId,
            @Parameter(description = "条数，默认50，最大200")
            @RequestParam(required = false, defaultValue = "50") Integer size) {
        return Result.ok(chatService.adminMessages(sessionId, size));
    }
}
