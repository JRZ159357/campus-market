// 文件路径: src/main/java/com/campus/market/service/ChatService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.ChatQueryDTO;
import com.campus.market.dto.ChatSendDTO;
import com.campus.market.dto.ChatSessionCreateDTO;
import com.campus.market.entity.ChatSession;
import com.campus.market.vo.ChatMessageVO;
import com.campus.market.vo.ChatSessionDetailVO;
import com.campus.market.vo.ChatSessionVO;
import com.campus.market.vo.UnreadSummaryVO;

import java.util.List;

/**
 * 聊天业务接口。
 * <p>
 * 所有方法都带 userId 参数：聊天数据是最敏感的越权场景，
 * 会话的参与者校验必须在服务层完成，不能依赖 Controller 或前端。
 */
public interface ChatService extends IService<ChatSession> {

    /** 我的会话列表（买家侧 + 卖家侧并集，按最后消息时间倒序） */
    IPage<ChatSessionVO> mySessions(Long userId, ChatQueryDTO query);

    /** 会话详情：头信息 + 最近一页消息（时间正序） */
    ChatSessionDetailVO detail(Long userId, Long sessionId);

    /** 创建或获取会话；首次创建会自动带一条商品卡片消息 */
    ChatSessionVO createOrGet(Long userId, ChatSessionCreateDTO dto);

    /** 从我的列表删除会话（只影响自己这一侧） */
    void deleteSession(Long userId, Long sessionId);

    /**
     * 某个商品下「我作为卖家」收到的全部咨询会话。
     * 「我的发布」里点「查看咨询」用：一个商品可能被多个买家咨询，所以要返回列表而不是单个会话。
     */
    List<ChatSessionVO> listByProduct(Long userId, Long productId);

    /** 历史消息（游标分页，倒序返回） */
    List<ChatMessageVO> history(Long userId, Long sessionId, Long cursor, int size);

    /** 发送消息：落库 + 更新会话摘要与未读 + WebSocket 推送 */
    ChatMessageVO sendMessage(Long userId, ChatSendDTO dto);

    /** 标记会话已读，返回对方 userId（供已读回执推送） */
    Long markRead(Long userId, Long sessionId);

    /** 查询会话中的对方 userId；无权限或会话不存在返回 null */
    Long peerOf(Long userId, Long sessionId);

    /** 未读数汇总（tabBar 角标） */
    UnreadSummaryVO unreadSummary(Long userId);

    /** 客服工作台：全平台会话分页（只读） */
    IPage<ChatSessionVO> adminSessions(ChatQueryDTO query);

    /** 客服工作台：查看某会话的聊天记录（只读） */
    List<ChatMessageVO> adminMessages(Long sessionId, int size);

    /**
     * 追加系统消息到指定买卖家的会话（订单状态变更时调用）。
     * 会话不存在时静默跳过——用户没聊过天，没必要凭空造一个会话出来。
     */
    void appendSystemMessage(Long buyerId, Long sellerId, Long productId, String text);
}
