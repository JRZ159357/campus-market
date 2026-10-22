// 文件路径: src/main/java/com/campus/market/service/impl/ChatServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.MsgType;
import com.campus.market.common.OrderStatus;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.SensitiveCheckResult;
import com.campus.market.dto.ChatQueryDTO;
import com.campus.market.dto.ChatSendDTO;
import com.campus.market.dto.ChatSessionCreateDTO;
import com.campus.market.entity.ChatMessage;
import com.campus.market.entity.ChatSession;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.entity.SysUser;
import com.campus.market.entity.TradeOrder;
import com.campus.market.mapper.ChatMessageMapper;
import com.campus.market.mapper.ChatSessionMapper;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.service.ChatService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.service.UnreadService;
import com.campus.market.vo.ChatMessageVO;
import com.campus.market.vo.ChatSessionDetailVO;
import com.campus.market.vo.ChatSessionVO;
import com.campus.market.vo.UnreadSummaryVO;
import com.campus.market.ws.WsFrame;
import com.campus.market.ws.WsSessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天业务实现。
 * <p>
 * 四个设计要点（论文可展开）：
 * <ol>
 *   <li><b>会话唯一性交给数据库</b>：同一对买卖家 + 同一商品只允许一个会话，靠
 *       (buyer_id, seller_id, product_key) 唯一索引保证。并发下两个请求同时创建，
 *       一个成功、另一个抛 {@link DuplicateKeyException}，捕获后重新查出来复用，
 *       因此不会出现「点两次联系卖家生成两个会话」；</li>
 *   <li><b>离线消息 = 落库 + 上线拉取</b>：消息一律先写库，再尝试 WebSocket 推送。
 *       对方不在线时推送直接跳过，他下次进入会话用游标拉历史消息即可，
 *       不需要维护离线队列或补偿任务；</li>
 *   <li><b>未读数冗余在会话行上</b>：会话列表要显示每个会话的未读数，
 *       若每行都去 chat_message 里 COUNT 未读就是 N+1 查询；这里在发送与已读时同步维护
 *       buyer_unread / seller_unread，列表查询零额外开销；</li>
 *   <li><b>游标分页</b>：历史消息用 {@code WHERE session_id=? AND id<?} 而不是 offset，
 *       聊天时新消息不断插入，offset 分页会因为数据前移导致重复或漏消息。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatService {

    /** 会话详情首屏消息条数 */
    private static final int DETAIL_PAGE_SIZE = 50;
    /** 历史消息默认条数 */
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    /** 系统消息的发送者 ID，0 表示平台 */
    private static final long SYSTEM_SENDER = 0L;

    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final SensitiveWordService sensitiveWordService;
    private final UnreadService unreadService;
    private final WsSessionManager wsSessionManager;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------

    @Override
    public IPage<ChatSessionVO> mySessions(Long userId, ChatQueryDTO query) {
        long current = pageNum(query.getPageNum());
        long size = pageSize(query.getPageSize(), 20);
        long offset = (current - 1) * size;
        List<ChatSession> rows = baseMapper.selectMySessions(userId, offset, size);
        long total = baseMapper.countMySessions(userId);

        IPage<ChatSessionVO> result = new Page<>(current, size, total);
        result.setRecords(toSessionVOList(rows, userId));
        return result;
    }

    @Override
    public ChatSessionDetailVO detail(Long userId, Long sessionId) {
        ChatSession session = requireMember(userId, sessionId);
        List<ChatMessage> rows = chatMessageMapper.selectHistory(sessionId, null, DETAIL_PAGE_SIZE + 1);
        boolean hasMore = rows.size() > DETAIL_PAGE_SIZE;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, DETAIL_PAGE_SIZE));
        }
        // 查询是倒序（新→旧），前端列表要从旧到新渲染
        Collections.reverse(rows);

        ChatSessionDetailVO vo = new ChatSessionDetailVO();
        vo.setSession(toSessionVO(session, userId,
                loadUsers(Collections.singletonList(peerIdOf(session, userId))),
                loadProducts(session.getProductId() == null ? List.of() : List.of(session.getProductId()))));
        vo.setMessages(toMessageVOList(rows, session, userId));
        vo.setHasMore(hasMore);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO createOrGet(Long userId, ChatSessionCreateDTO dto) {
        if (userId.equals(dto.getSellerId())) {
            throw new BusinessException("不能和自己聊天");
        }
        SysUser other = sysUserMapper.selectById(dto.getSellerId());
        if (other == null || Integer.valueOf(1).equals(other.getStatus())) {
            throw new BusinessException("对方账号不存在或已被封禁");
        }

        Product product = null;
        if (dto.getProductId() != null) {
            product = productMapper.selectById(dto.getProductId());
            if (product == null) {
                throw new BusinessException("商品不存在或已被删除");
            }
        }

        /*
         * 角色归一化：会话表里的 buyer_id / seller_id 是「咨询方 / 被咨询方」而不是严格的买卖身份。
         * 商品详情的「联系卖家」是买家发起，当前用户就是 buyer；
         * 订单详情的「联系买家」是卖家发起，此时商品的卖家是当前用户，必须把角色倒过来，
         * 否则同一对用户对同一商品会生成两个方向相反的会话（唯一索引拦不住）。
         */
        boolean sellerInitiating = product != null && userId.equals(product.getSellerId());
        Long buyerId = sellerInitiating ? dto.getSellerId() : userId;
        Long sellerId = sellerInitiating ? userId : dto.getSellerId();
        if (product != null && !product.getSellerId().equals(sellerId)) {
            throw new BusinessException("该商品不属于这个卖家");
        }

        ChatSession session = findSession(buyerId, sellerId, dto.getProductId());
        boolean created = false;
        if (session == null) {
            session = new ChatSession();
            session.setBuyerId(buyerId);
            session.setSellerId(sellerId);
            session.setProductId(dto.getProductId());
            session.setBuyerUnread(0);
            session.setSellerUnread(0);
            session.setBuyerDeleted(0);
            session.setSellerDeleted(0);
            session.setLastTime(LocalDateTime.now());
            try {
                save(session);
                created = true;
            } catch (DuplicateKeyException e) {
                // 并发创建：另一个请求已经建好了，直接复用
                session = findSession(buyerId, sellerId, dto.getProductId());
                if (session == null) {
                    throw new BusinessException("会话创建失败，请重试");
                }
            }
        }

        // 买家第一次点「联系卖家」时自动发一张商品卡片，卖家一眼就知道问的是哪件商品
        if (created && product != null && !sellerInitiating) {
            ChatSendDTO card = new ChatSendDTO();
            card.setSessionId(session.getId());
            card.setMsgType(MsgType.PRODUCT_CARD.getCode());
            card.setContent(String.valueOf(product.getId()));
            sendMessage(userId, card);
            session = getById(session.getId());
        }

        return toSessionVO(session, userId,
                loadUsers(Collections.singletonList(sellerInitiating ? buyerId : sellerId)),
                loadProducts(dto.getProductId() == null ? List.of() : List.of(dto.getProductId())));
    }

    @Override
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = requireMember(userId, sessionId);
        boolean asBuyer = session.getBuyerId().equals(userId);
        update(Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .set(asBuyer, ChatSession::getBuyerDeleted, 1)
                .set(!asBuyer, ChatSession::getSellerDeleted, 1));
        unreadService.refresh(userId);
    }

    @Override
    public List<ChatSessionVO> listByProduct(Long userId, Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }
        List<ChatSession> rows = list(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getProductId, productId)
                .eq(ChatSession::getSellerId, userId)
                .eq(ChatSession::getSellerDeleted, 0)
                .orderByDesc(ChatSession::getLastTime));
        return toSessionVOList(rows, userId);
    }

    // ------------------------------------------------------------------
    // 消息
    // ------------------------------------------------------------------

    @Override
    public List<ChatMessageVO> history(Long userId, Long sessionId, Long cursor, int size) {
        ChatSession session = requireMember(userId, sessionId);
        int limit = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        List<ChatMessage> rows = chatMessageMapper.selectHistory(sessionId, cursor, limit);
        return toMessageVOList(rows, session, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendMessage(Long userId, ChatSendDTO dto) {
        ChatSession session = requireMember(userId, dto.getSessionId());
        Long receiverId = peerIdOf(session, userId);

        int msgType = dto.getMsgType() == null ? MsgType.TEXT.getCode() : dto.getMsgType();
        if (MsgType.SYSTEM.getCode().equals(msgType)) {
            throw new BusinessException("系统消息不允许手动发送");
        }
        String content = normalizeContent(msgType, dto.getContent(), userId, receiverId);

        /*
         * 敏感词过滤（模块11）。
         *
         * 只对纯文本消息生效：商品卡与订单卡的内容是服务端生成的 JSON，
         * 拿去匹配敏感词既没有意义（里面不会有人写违禁词），
         * 还可能把 JSON 里的字段值替换成 ** 导致前端解析失败。
         *
         * 处理策略与商品/评价不同——这里不拒绝整条消息，只替换命中词：
         * 聊天是即时通讯，直接吞消息会让发送者以为网络坏了反复重发，
         * 而替换成 ** 既阻断了违规内容传播，又给了即时反馈。
         * 警告级的词正常发送，只记一条日志供事后追溯。
         */
        if (MsgType.TEXT.getCode().equals(msgType)) {
            SensitiveCheckResult sensitive = sensitiveWordService.check(content);
            if (sensitive.isBlocked()) {
                String masked = sensitive.maskBlockWords(content);
                log.info("聊天消息命中拦截级敏感词，已脱敏: sessionId={}, senderId={}, words={}",
                        session.getId(), userId, sensitive.joinWords());
                content = masked;
            } else if (sensitive.isWarned()) {
                log.info("聊天消息命中警告级敏感词（正常发送）: sessionId={}, senderId={}, words={}",
                        session.getId(), userId, sensitive.joinWords());
            }
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(userId);
        message.setReceiverId(receiverId);
        message.setMsgType(msgType);
        message.setContent(content);
        message.setIsRead(0);
        chatMessageMapper.insert(message);

        boolean receiverIsBuyer = receiverId.equals(session.getBuyerId());
        update(Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getLastMessage, MsgType.preview(msgType, content))
                .set(ChatSession::getLastTime, message.getCreateTime())
                // 收到新消息，双方都不应该再从自己列表里看不到它
                .set(ChatSession::getBuyerDeleted, 0)
                .set(ChatSession::getSellerDeleted, 0)
                .setSql(receiverIsBuyer
                        ? "buyer_unread = buyer_unread + 1"
                        : "seller_unread = seller_unread + 1"));

        ChatMessageVO senderView = toMessageVO(message, session, userId);
        /*
         * 推送用的必须是「接收方视角」的 VO：self 表示「这条是不是我发的」，
         * 如果直接复用发送方的 VO（self=true），对方的聊天页会把这条消息渲染到右侧，
         * 看起来像是自己发的。同一条数据，两个视角各生成一次。
         */
        ChatMessageVO receiverView = toMessageVO(message, session, receiverId);
        // 先落库再推送：推送失败不影响消息本身，对方上线拉历史即可
        wsSessionManager.push(receiverId, WsFrame.of(WsFrame.TYPE_MESSAGE, receiverView));
        unreadService.refresh(receiverId);
        unreadService.refresh(userId);
        return senderView;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long markRead(Long userId, Long sessionId) {
        ChatSession session = requireMember(userId, sessionId);
        chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, 0)
                .set(ChatMessage::getIsRead, 1));
        boolean asBuyer = session.getBuyerId().equals(userId);
        update(Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .set(asBuyer, ChatSession::getBuyerUnread, 0)
                .set(!asBuyer, ChatSession::getSellerUnread, 0));
        unreadService.refresh(userId);
        return peerIdOf(session, userId);
    }

    @Override
    public Long peerOf(Long userId, Long sessionId) {
        return peerIdOf(requireMember(userId, sessionId), userId);
    }

    @Override
    public UnreadSummaryVO unreadSummary(Long userId) {
        return unreadService.summary(userId);
    }

    // ------------------------------------------------------------------
    // 客服工作台（只读）
    // ------------------------------------------------------------------

    @Override
    public IPage<ChatSessionVO> adminSessions(ChatQueryDTO query) {
        long current = pageNum(query.getPageNum());
        long size = pageSize(query.getPageSize(), 10);
        List<Long> userIds = null;
        if (StringUtils.hasText(query.getKeyword())) {
            userIds = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .select(SysUser::getId)
                            .like(SysUser::getNickname, query.getKeyword().trim()))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (userIds.isEmpty()) {
                // 昵称匹配不到人时直接返回空页，避免退化成全表扫描
                IPage<ChatSessionVO> empty = new Page<>(current, size, 0);
                empty.setRecords(Collections.emptyList());
                return empty;
            }
        }
        long total = baseMapper.countAllSessions(userIds);
        List<ChatSession> rows = total == 0
                ? Collections.emptyList()
                : baseMapper.selectAllSessions(userIds, (current - 1) * size, size);

        IPage<ChatSessionVO> result = new Page<>(current, size, total);
        result.setRecords(toSessionVOList(rows, null));
        return result;
    }

    @Override
    public List<ChatMessageVO> adminMessages(Long sessionId, int size) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        int limit = size <= 0 ? DETAIL_PAGE_SIZE : Math.min(size, 200);
        List<ChatMessage> rows = chatMessageMapper.selectHistory(sessionId, null, limit);
        Collections.reverse(rows);
        // 客服不是参与者，self 恒为 false；左右气泡改由 fromBuyer 决定
        return toMessageVOList(rows, session, null);
    }

    // ------------------------------------------------------------------
    // 系统消息
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendSystemMessage(Long buyerId, Long sellerId, Long productId, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        ChatSession session = productId == null
                ? findAnySession(buyerId, sellerId)
                : findSession(buyerId, sellerId, productId);
        if (session == null) {
            // 双方没有聊过天就不必凭空造一个会话，通知(notice)已经覆盖了提醒职责
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(SYSTEM_SENDER);
        message.setReceiverId(0L);
        message.setMsgType(MsgType.SYSTEM.getCode());
        message.setContent(text);
        message.setIsRead(1);
        chatMessageMapper.insert(message);

        // 系统消息不计入未读，否则每一笔订单变动都会让角标 +2，用户会被噪音淹没
        update(Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getLastMessage, MsgType.preview(MsgType.SYSTEM.getCode(), text))
                .set(ChatSession::getLastTime, message.getCreateTime()));

        ChatMessageVO vo = toMessageVO(message, session, null);
        wsSessionManager.push(buyerId, WsFrame.of(WsFrame.TYPE_MESSAGE, vo));
        wsSessionManager.push(sellerId, WsFrame.of(WsFrame.TYPE_MESSAGE, vo));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 校验当前用户是不是会话参与者，并返回会话。不是参与者一律按「会话不存在」处理，
     * 避免通过错误码泄露「这个会话存在但你无权查看」。
     */
    private ChatSession requireMember(Long userId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException("会话不存在");
        }
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    private Long peerIdOf(ChatSession session, Long userId) {
        return session.getBuyerId().equals(userId) ? session.getSellerId() : session.getBuyerId();
    }

    private ChatSession findSession(Long buyerId, Long sellerId, Long productId) {
        return getOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getBuyerId, buyerId)
                .eq(ChatSession::getSellerId, sellerId)
                .eq(productId != null, ChatSession::getProductId, productId)
                .isNull(productId == null, ChatSession::getProductId)
                .last("limit 1"), false);
    }

    /** 双方之间任意一个会话（订单级系统消息不关心具体商品） */
    private ChatSession findAnySession(Long buyerId, Long sellerId) {
        return getOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getBuyerId, buyerId)
                .eq(ChatSession::getSellerId, sellerId)
                .orderByDesc(ChatSession::getLastTime)
                .last("limit 1"), false);
    }

    /**
     * 卡片消息的 content 由服务端按 ID 重新生成 JSON，不信任客户端传来的内容：
     * 否则前端可以把任意 JSON 塞进卡片，客服工作台渲染时就可能出错或显示伪造信息。
     */
    private String normalizeContent(int msgType, String raw, Long userId, Long receiverId) {
        MsgType type = MsgType.of(msgType);
        try {
            switch (type) {
                case PRODUCT_CARD -> {
                    Product product = productMapper.selectById(Long.valueOf(raw.trim()));
                    if (product == null) {
                        throw new BusinessException("商品不存在或已被删除");
                    }
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("productId", product.getId());
                    card.put("title", product.getTitle());
                    card.put("price", product.getPrice());
                    card.put("cover", coverOf(product.getId()));
                    card.put("status", product.getStatus());
                    card.put("statusLabel", ProductStatus.label(product.getStatus()));
                    return objectMapper.writeValueAsString(card);
                }
                case ORDER_CARD -> {
                    TradeOrder order = tradeOrderMapper.selectOne(Wrappers.<TradeOrder>lambdaQuery()
                            .eq(TradeOrder::getOrderNo, raw.trim()).last("limit 1"));
                    if (order == null) {
                        throw new BusinessException("订单不存在");
                    }
                    if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
                        throw new BusinessException("订单不存在");
                    }
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("orderNo", order.getOrderNo());
                    card.put("price", order.getPrice());
                    card.put("status", order.getStatus());
                    card.put("statusLabel", OrderStatus.label(order.getStatus()));
                    card.put("cover", coverOf(order.getProductId()));
                    card.put("title", productTitleOf(order.getProductId()));
                    return objectMapper.writeValueAsString(card);
                }
                default -> {
                    return raw == null ? "" : raw.trim();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("消息内容格式错误");
        }
    }

    private List<ChatSessionVO> toSessionVOList(List<ChatSession> rows, Long viewerId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (ChatSession row : rows) {
            userIds.add(row.getBuyerId());
            userIds.add(row.getSellerId());
            if (row.getProductId() != null) {
                productIds.add(row.getProductId());
            }
        }
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, Product> products = loadProducts(productIds);

        List<ChatSessionVO> list = new ArrayList<>(rows.size());
        for (ChatSession row : rows) {
            list.add(buildSessionVO(row, viewerId, users, products));
        }
        return list;
    }

    private ChatSessionVO toSessionVO(ChatSession session, Long viewerId,
                                      Map<Long, SysUser> users, Map<Long, Product> products) {
        return buildSessionVO(session, viewerId, users, products);
    }

    private ChatSessionVO buildSessionVO(ChatSession session, Long viewerId,
                                         Map<Long, SysUser> users, Map<Long, Product> products) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setProductId(session.getProductId());
        vo.setLastMessage(session.getLastMessage());
        vo.setLastTime(session.getLastTime());
        vo.setBuyerId(session.getBuyerId());
        vo.setSellerId(session.getSellerId());

        SysUser buyer = users.get(session.getBuyerId());
        SysUser seller = users.get(session.getSellerId());
        vo.setBuyerNickname(buyer == null ? "用户已注销" : buyer.getNickname());
        vo.setSellerNickname(seller == null ? "用户已注销" : seller.getNickname());

        if (viewerId != null) {
            boolean asBuyer = session.getBuyerId().equals(viewerId);
            vo.setBuyer(asBuyer);
            vo.setUnread(asBuyer ? session.getBuyerUnread() : session.getSellerUnread());
            SysUser peer = asBuyer ? seller : buyer;
            vo.setPeerId(peer == null ? null : peer.getId());
            vo.setPeerNickname(peer == null ? "用户已注销" : peer.getNickname());
            vo.setPeerAvatar(peer == null ? null : peer.getAvatar());
            vo.setPeerVerified(peer != null && Integer.valueOf(1).equals(peer.getStudentVerified()));
        } else {
            // 客服视角：不区分「我方」，只提供双方信息。
            // unread 取双方未读之和，表示「这个会话里还有几条消息没人看」，用于列表上的红点提示
            vo.setBuyer(false);
            vo.setUnread((session.getBuyerUnread() == null ? 0 : session.getBuyerUnread())
                    + (session.getSellerUnread() == null ? 0 : session.getSellerUnread()));
        }

        Product product = session.getProductId() == null ? null : products.get(session.getProductId());
        if (product != null) {
            vo.setProductTitle(product.getTitle());
            vo.setProductCover(coverOf(product.getId()));
        }
        return vo;
    }

    private List<ChatMessageVO> toMessageVOList(List<ChatMessage> rows, ChatSession session, Long viewerId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> senderIds = rows.stream().map(ChatMessage::getSenderId)
                .filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        Map<Long, SysUser> users = loadUsers(senderIds);
        List<ChatMessageVO> list = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            ChatMessageVO vo = toMessageVO(row, session, viewerId);
            SysUser sender = users.get(row.getSenderId());
            if (sender != null) {
                vo.setSenderNickname(sender.getNickname());
                vo.setSenderAvatar(sender.getAvatar());
            } else if (SYSTEM_SENDER == (row.getSenderId() == null ? -1 : row.getSenderId())) {
                vo.setSenderNickname("系统");
            }
            list.add(vo);
        }
        return list;
    }

    private ChatMessageVO toMessageVO(ChatMessage message, ChatSession session, Long viewerId) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setMsgType(message.getMsgType());
        vo.setMsgTypeLabel(MsgType.of(message.getMsgType()).getLabel());
        vo.setContent(message.getContent());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(message.getCreateTime());
        vo.setSelf(viewerId != null && viewerId.equals(message.getSenderId()));
        vo.setFromBuyer(session.getBuyerId().equals(message.getSenderId()));
        if (MsgType.PRODUCT_CARD.getCode().equals(message.getMsgType())
                || MsgType.ORDER_CARD.getCode().equals(message.getMsgType())) {
            vo.setCard(parseCard(message.getContent()));
        }
        return vo;
    }

    private Map<String, Object> parseCard(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            // 历史脏数据不能拖垮整个消息列表，降级为空卡片由前端显示占位
            return Collections.emptyMap();
        }
    }

    private Map<Long, SysUser> loadUsers(List<Long> ids) {
        List<Long> distinct = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, Product> loadProducts(List<Long> ids) {
        List<Long> distinct = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    private String coverOf(Long productId) {
        if (productId == null) {
            return null;
        }
        ProductImage cover = productImageMapper.selectOne(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, productId)
                .orderByAsc(ProductImage::getSort)
                .orderByAsc(ProductImage::getId)
                .last("limit 1"));
        return cover == null ? null : cover.getImageUrl();
    }

    private String productTitleOf(Long productId) {
        if (productId == null) {
            return null;
        }
        Product product = productMapper.selectById(productId);
        return product == null ? null : product.getTitle();
    }

    private long pageNum(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long pageSize(Long value, long def) {
        if (value == null || value < 1) {
            return def;
        }
        return Math.min(value, 100);
    }
}
