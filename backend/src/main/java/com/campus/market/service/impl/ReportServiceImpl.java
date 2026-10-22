// 文件路径: src/main/java/com/campus/market/service/impl/ReportServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.CreditLogType;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.ReportHandleAction;
import com.campus.market.common.ReportReason;
import com.campus.market.common.ReportRule;
import com.campus.market.common.ReportStatus;
import com.campus.market.common.ReportType;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.dto.AdminUserStatusDTO;
import com.campus.market.dto.ReportHandleDTO;
import com.campus.market.dto.ReportQueryDTO;
import com.campus.market.dto.ReportSubmitDTO;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.entity.Report;
import com.campus.market.entity.Review;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ReportMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.AdminUserService;
import com.campus.market.service.CreditService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.ProductService;
import com.campus.market.service.ReportService;
import com.campus.market.service.ReviewService;
import com.campus.market.vo.ReportStatsVO;
import com.campus.market.vo.ReportVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 举报业务实现。
 * <p>
 * 本类的核心是两件事，其余都是围绕它们的胶水：
 * <ol>
 *   <li><b>快照取证</b>：提交举报时把被举报对象序列化进 {@code target_snapshot}，
 *       让「违规商品被下架后才轮到客服处理」「违规评价被删了才想起看举报」
 *       这两种常见情形仍然有据可查；</li>
 *   <li><b>处置联动</b>：处理动作真正落到业务数据上——下架商品、封禁用户、
 *       删评价、扣信誉分，并给举报人与被举报人<b>双方</b>发通知。
 *       只在举报表里改个 status 字段等于没处理。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ReviewMapper reviewMapper;
    private final SysUserMapper sysUserMapper;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final AdminUserService adminUserService;
    private final CreditService creditService;
    private final NoticeService noticeService;
    private final DashboardCacheService dashboardCacheService;
    private final ObjectMapper objectMapper;

    // ==================== 提交 ====================

    @Override
    public Long submit(Long reporterId, ReportSubmitDTO dto) {
        int type = dto.getReportType();
        if (!ReportType.isValid(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报类型不合法");
        }
        if (dto.getReason() != null && !ReportReason.isValid(dto.getReason())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报原因不合法");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.length() < ReportRule.CONTENT_MIN_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "详细描述至少 " + ReportRule.CONTENT_MIN_LENGTH + " 字，方便客服判断");
        }
        List<String> images = normalizeImages(dto.getImages());

        // 1）目标存在性与归属人校验：对象不存在直接拒绝，不存「指向空气」的举报
        TargetInfo target = loadTarget(type, dto.getTargetId());

        // 2）不能举报自己：否则可以给自己刷「举报成立」的扣分记录，或刷举报数
        if (target.ownerId().equals(reporterId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能举报自己的内容");
        }

        // 3）24 小时节流：没有节流的话，一条商品被同一用户刷 100 条，
        //    客服待处理列表就被淹没，其他真实举报反而没人管
        LocalDateTime last = baseMapper.selectLastReportTime(reporterId, type, dto.getTargetId());
        if (last != null) {
            long hours = Duration.between(last, LocalDateTime.now()).toHours();
            if (hours < ReportRule.DUP_COOLDOWN_HOURS) {
                long remain = ReportRule.DUP_COOLDOWN_HOURS - hours;
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "你已在 " + hours + " 小时内举报过该内容，请等待 " + remain + " 小时后再试");
            }
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setReportType(type);
        report.setTargetId(dto.getTargetId());
        report.setTargetUserId(target.ownerId());
        report.setTargetSnapshot(writeSnapshot(target.snapshot()));
        report.setReason(dto.getReason());
        report.setContent(content);
        report.setImages(images == null ? null : String.join(",", images));
        report.setStatus(ReportStatus.PENDING);
        report.setHandleAction(ReportHandleAction.NONE);
        this.save(report);
        // 新举报会让「待处理举报」增加、趋势图多一天的数据
        dashboardCacheService.evictOnReportChange();
        log.info("用户 {} 提交{}，targetId={}", reporterId, ReportType.label(type), dto.getTargetId());
        return report.getId();
    }

    // ==================== 我的举报 ====================

    @Override
    public IPage<ReportVO> myPage(Long reporterId, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .orderByDesc(Report::getId);
        IPage<Report> result = this.page(new Page<>(current, size), wrapper);
        Page<ReportVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public ReportVO myDetail(Long reporterId, Long id) {
        Report report = this.getById(id);
        // 别人的举报记录一律报「不存在」而不是 403：
        // 返回 403 等于确认了「这个 ID 存在」，就成了可遍历的探测接口
        if (report == null || !report.getReporterId().equals(reporterId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "举报记录不存在");
        }
        return toVO(report);
    }

    // ==================== 管理端 ====================

    @Override
    public IPage<ReportVO> adminPage(ReportQueryDTO query, Long currentUserId) {
        ReportQueryDTO condition = query == null ? new ReportQueryDTO() : query;
        long current = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        long size = condition.getPageSize() == null || condition.getPageSize() < 1 ? 10
                : Math.min(condition.getPageSize(), 100);
        IPage<ReportVO> result = baseMapper.selectAdminPage(new Page<>(current, size), condition);
        // SQL 只负责「筛出哪几行 + JOIN 出昵称」，展示用的文案与快照仍走统一装配，
        // 否则列表页的类型列、状态列、对象标题会全是空白（详情接口却正常）
        result.getRecords().forEach(vo -> {
            fillDerived(vo);
            vo.setHandleHours(handleHours(vo));
            // 「这条是不是我接的」：客服同时处理多单时，
            // 靠处理人名字判断很容易看串（两个客服都叫「小周」也不稀奇）
            vo.setAcceptedByMe(currentUserId != null && currentUserId.equals(vo.getHandlerId()));
        });
        return result;
    }

    @Override
    public ReportVO adminDetail(Long id) {
        Report report = this.getById(id);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "举报记录不存在");
        }
        return toVO(report);
    }

    // ==================== 接单 ====================

    @Override
    @OpLog(module = OpLogModule.REPORT, action = OpLogModule.ACTION_ACCEPT, targetIndex = 1,
            desc = "接单举报")
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long reportId, Long handlerId) {
        Report report = this.getById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "举报记录不存在");
        }
        if (report.getStatus() == null || report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "该举报当前状态为「" + ReportStatus.label(report.getStatus()) + "」，无法接单");
        }
        // 条件更新：并发下只有一个客服能把 status 从 0 改成 1，
        // 第二个拿到 rows=0，从而得到「已被他人接单」而不是静默覆盖 handler_id
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId)
                .eq(Report::getStatus, ReportStatus.PENDING)
                .set(Report::getStatus, ReportStatus.PROCESSING)
                .set(Report::getHandlerId, handlerId)
                .set(Report::getAcceptTime, LocalDateTime.now())
                .set(Report::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该举报已被他人接单");
        }
        // 接单把状态从「待处理」改成「处理中」，待处理口径不变，
        // 但接单本身是一次写操作，顺手失效一下成本极低
        dashboardCacheService.evictOnReportChange();
    }

    // ==================== 处理 ====================

    @Override
    @OpLog(module = OpLogModule.REPORT, action = OpLogModule.ACTION_HANDLE, targetIndex = 1,
            desc = "处理举报")
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long reportId, ReportHandleDTO dto, Long operatorId) {
        Report report = this.getById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "举报记录不存在");
        }
        if (ReportStatus.isFinal(report.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "该举报当前状态为「" + ReportStatus.label(report.getStatus()) + "」，不能重复处理");
        }

        int status = dto.getStatus();
        int action = dto.getHandleAction() == null ? ReportHandleAction.NONE : dto.getHandleAction();
        if (status != ReportStatus.HANDLED && status != ReportStatus.REJECTED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "处理结论只能是已处理或已驳回");
        }
        // 驳回时不允许带处置动作，否则会出现「状态说驳回，却把人封了」的矛盾数据
        if (status == ReportStatus.REJECTED && action != ReportHandleAction.NONE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报不成立时不能执行处置动作");
        }
        // 举报成立却一个动作都不执行，同样是矛盾数据：
        // 举报人被告知「已处理」，但违规内容还挂在那儿，等于处理了个寂寞。
        // 正确做法是把结论改成「举报不成立」，而不是硬标成已处理。
        if (status == ReportStatus.HANDLED && action == ReportHandleAction.NONE) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "举报成立时必须选择处置动作；若不成立请将结论改为「举报不成立」");
        }
        if (status == ReportStatus.HANDLED && !ReportType.allowedActions(report.getReportType()).contains(action)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "「" + ReportHandleAction.shortLabel(action) + "」不适用于"
                            + ReportType.label(report.getReportType()));
        }
        String result = dto.getHandleResult() == null ? "" : dto.getHandleResult().trim();
        if (result.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写处理结果");
        }

        // 处置先执行再落状态：动作失败就整体回滚，
        // 不会出现「状态已处理但商品还在架上」的半完成态
        String actionSummary = applyAction(report, action, operatorId);

        Report update = new Report();
        update.setId(reportId);
        update.setStatus(status);
        update.setHandleAction(action);
        update.setHandleResult(actionSummary.isEmpty()
                ? result : result + "（已执行：" + actionSummary + "）");
        update.setHandleTime(LocalDateTime.now());
        // 管理员不接单直接判定时把接单时间补成提交时间，
        // 否则 accept_time 为空会让这条记录不计入平均处理时长，统计悄悄少算样本
        if (report.getAcceptTime() == null) {
            update.setAcceptTime(report.getCreateTime());
            update.setHandlerId(operatorId);
        }
        update.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(update);

        notifyBothSides(report, status, result, actionSummary);
        // 举报结案会让「待处理举报」减少；处置动作若涉及商品下架或评价删除，
        // 对应的商品/评价缓存已由各自的 Service 失效，这里只需管举报口径
        dashboardCacheService.evictOnReportChange();
        log.info("处理人 {} 处理举报 {}，结论={}，动作={}",
                operatorId, reportId, ReportStatus.label(status), ReportHandleAction.shortLabel(action));
    }

    /**
     * 执行处置动作，返回可读摘要（拼进 handle_result 给双方看）。
     * <p>
     * 失败一律向上抛异常让事务回滚：处置做了一半（商品下了架但封号失败）
     * 却不写处理记录，比直接报错更糟——客服会以为处理过了。
     */
    private String applyAction(Report report, int action, Long operatorId) {
        if (!ReportHandleAction.isViolation(action)) {
            return "";
        }
        return switch (action) {
            case ReportHandleAction.PRODUCT_OFFLINE -> offlineReportedProduct(report);
            case ReportHandleAction.USER_BAN -> banReportedUser(report, operatorId);
            case ReportHandleAction.REVIEW_DELETE -> deleteReportedReview(report, operatorId);
            case ReportHandleAction.CREDIT_DEDUCT -> deductCreditOnly(report);
            default -> "";
        };
    }

    /** 强制下架被举报商品 + 扣卖家信誉分 */
    private String offlineReportedProduct(Report report) {
        Product product = productMapper.selectById(report.getTargetId());
        if (product == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "被举报商品不存在或已删除，无法执行下架");
        }
        StringBuilder sb = new StringBuilder();
        if (ProductStatus.OFFLINEABLE_STATUSES.contains(product.getStatus())) {
            productService.forceOfflineByReport(product.getId(), null,
                    "举报核实：商品违反平台规则（举报#" + report.getId() + "）");
            sb.append("商品已强制下架");
        } else {
            sb.append("商品已是「").append(ProductStatus.label(product.getStatus())).append("」状态，无需再下架");
        }
        int delta = creditService.addScore(product.getSellerId(), ReportRule.PRODUCT_VIOLATION_CREDIT,
                CreditLogType.REPORT, "举报核实违规：商品被强制下架（举报#" + report.getId() + "）",
                report.getId(), null);
        sb.append("，卖家信誉分").append(delta).append("分");
        return sb.toString();
    }

    /**
     * 封禁被举报用户 + 扣信誉分。
     * <p>
     * 刻意复用 {@code adminUserService.updateStatus} 而不是自己写一遍 UPDATE：
     * 那里面已经包含「下架其全部在架商品」「发通知」「清缓存」三个联动，
     * 这里重写一遍必然漏掉其中一个（尤其清缓存——
     * 漏了会导致被封的用户在缓存过期前仍能正常访问）。
     */
    private String banReportedUser(Report report, Long operatorId) {
        SysUser user = sysUserMapper.selectById(report.getTargetId());
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "被举报用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getStatus())) {
            return "该用户已处于封禁状态";
        }
        AdminUserStatusDTO dto = new AdminUserStatusDTO();
        dto.setStatus(1);
        dto.setReason("举报核实：违反平台规则（举报#" + report.getId() + "）");
        adminUserService.updateStatus(user.getId(), dto, operatorId);
        int delta = creditService.addScore(user.getId(), ReportRule.USER_VIOLATION_CREDIT,
                CreditLogType.REPORT, "举报核实违规：账号被封禁（举报#" + report.getId() + "）",
                report.getId(), null);
        return "用户已封禁，信誉分" + delta + "分";
    }

    /**
     * 删除被举报评价 + 扣评价人信誉分。
     * <p>
     * 为什么评价人要扣分：被评价人已经拿不到这条好评了，
     * 不扣评价人的分就会形成「靠发违规评价刷信誉分」的无成本套利。
     */
    private String deleteReportedReview(Report report, Long operatorId) {
        Review review = reviewMapper.selectById(report.getTargetId());
        if (review == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "被举报评价不存在或已被删除");
        }
        // 走 ReviewService.adminDelete：它内部已写「留审计流水但不回退信誉分」，
        // 自己重写会漏掉这条审计痕迹
        reviewService.adminDelete(review.getId(), operatorId);
        int delta = creditService.addScore(review.getReviewerId(), ReportRule.REVIEW_VIOLATION_CREDIT,
                CreditLogType.REPORT, "举报核实违规：违规评价被删除（举报#" + report.getId() + "）",
                report.getId(), null);
        return "违规评价已删除，评价人信誉分" + delta + "分";
    }

    /** 只扣信誉分，不删除任何内容（适用于「内容没问题但行为有问题的轻违规」） */
    private String deductCreditOnly(Report report) {
        Long targetUserId = resolveCreditTargetUserId(report);
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "找不到该举报对应的用户，无法扣减信誉分");
        }
        int delta = creditService.addScore(targetUserId, ReportRule.USER_VIOLATION_CREDIT,
                CreditLogType.REPORT, "举报核实违规扣分（举报#" + report.getId() + "）",
                report.getId(), null);
        return "信誉分" + delta + "分";
    }

    /**
     * 扣分目标：商品举报扣卖家、评价举报扣评价人、用户举报扣被举报人。
     * <p>
     * 快照里的 target_user_id 是提交时刻的归属人，但用户可能已经把商品删了、
     * 评价被清理了；此时该字段仍能定位到人，所以优先用它，
     * 只有它为空（历史脏数据）才回退到实时查询。
     */
    private Long resolveCreditTargetUserId(Report report) {
        if (report.getTargetUserId() != null) {
            return report.getTargetUserId();
        }
        int type = report.getReportType() == null ? 0 : report.getReportType();
        if (type == ReportType.PRODUCT) {
            Product p = productMapper.selectById(report.getTargetId());
            return p == null ? null : p.getSellerId();
        }
        if (type == ReportType.REVIEW) {
            Review r = reviewMapper.selectById(report.getTargetId());
            return r == null ? null : r.getReviewerId();
        }
        return report.getTargetId();
    }

    // ==================== 统计 ====================

    @Override
    public ReportStatsVO stats() {
        ReportStatsVO vo = new ReportStatsVO();
        vo.setPendingCount(0L);
        vo.setProcessingCount(0L);
        vo.setHandledCount(0L);
        vo.setRejectedCount(0L);

        long total = 0;
        for (Map<String, Object> row : baseMapper.countGroupByStatus()) {
            Object statusObj = row.get("status");
            Object cntObj = row.get("cnt");
            int status = statusObj == null ? -1 : Integer.parseInt(String.valueOf(statusObj));
            long cnt = cntObj == null ? 0L : Long.parseLong(String.valueOf(cntObj));
            total += cnt;
            switch (status) {
                case ReportStatus.PENDING -> vo.setPendingCount(cnt);
                case ReportStatus.PROCESSING -> vo.setProcessingCount(cnt);
                case ReportStatus.HANDLED -> vo.setHandledCount(cnt);
                case ReportStatus.REJECTED -> vo.setRejectedCount(cnt);
                default -> {
                }
            }
        }
        vo.setTotalCount(total);

        Map<String, Object> today = baseMapper.countToday();
        vo.setTodayCount(toLong(today == null ? null : today.get("todayCount")));
        vo.setTodayHandledCount(toLong(today == null ? null : today.get("todayHandledCount")));

        // 处理率分母含已驳回：驳回也是「客服给了结论」，
        // 排除它会让客服越认真处理、处理率反而越低
        long finished = vo.getHandledCount() + vo.getRejectedCount();
        vo.setHandleRate(total == 0 ? 0.0 : round((double) finished / total * 100) / 100.0);

        List<Integer> durations = baseMapper.selectHandleDurations();
        if (durations != null && !durations.isEmpty()) {
            double avgMinutes = durations.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .filter(v -> v >= 0)
                    .average().orElse(0.0);
            vo.setAvgHandleHours(round(avgMinutes / 60.0 * 100) / 100.0);
        } else {
            vo.setAvgHandleHours(0.0);
        }
        return vo;
    }

    // ==================== 快照 ====================

    /**
     * 读取被举报对象的当前信息并封装成快照。
     * <p>
     * 三种类型取的关键字段不同，共同要求是「客服判断违规与否所必需的」：
     * 商品要标题+价格+描述+主图，用户要昵称+信誉分+认证状态，评价要星级+内容+配图。
     */
    private TargetInfo loadTarget(int type, Long targetId) {
        return switch (type) {
            case ReportType.PRODUCT -> {
                Product product = productMapper.selectById(targetId);
                if (product == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "被举报的商品不存在或已删除");
                }
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("kind", "product");
                snapshot.put("title", product.getTitle());
                snapshot.put("description", product.getDescription());
                snapshot.put("price", product.getPrice());
                snapshot.put("status", product.getStatus());
                snapshot.put("statusLabel", ProductStatus.label(product.getStatus()));
                snapshot.put("cover", firstImage(targetId));
                snapshot.put("createTime", String.valueOf(product.getCreateTime()));
                yield new TargetInfo(product.getSellerId(), snapshot);
            }
            case ReportType.USER -> {
                SysUser user = sysUserMapper.selectById(targetId);
                if (user == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "被举报的用户不存在");
                }
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("kind", "user");
                snapshot.put("nickname", user.getNickname());
                snapshot.put("avatar", user.getAvatar());
                snapshot.put("intro", user.getIntro());
                snapshot.put("role", user.getRole());
                snapshot.put("roleLabel", RoleConst.label(user.getRole()));
                snapshot.put("studentVerified", user.getStudentVerified());
                snapshot.put("creditScore", user.getCreditScore());
                snapshot.put("status", user.getStatus());
                yield new TargetInfo(user.getId(), snapshot);
            }
            case ReportType.REVIEW -> {
                Review review = reviewMapper.selectById(targetId);
                if (review == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "被举报的评价不存在或已删除");
                }
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("kind", "review");
                snapshot.put("stars", review.getStars());
                snapshot.put("content", review.getContent());
                snapshot.put("images", review.getImages());
                snapshot.put("isAnonymous", review.getIsAnonymous());
                snapshot.put("productId", review.getProductId());
                snapshot.put("orderNo", review.getOrderNo());
                snapshot.put("createTime", String.valueOf(review.getCreateTime()));
                yield new TargetInfo(review.getReviewerId(), snapshot);
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "举报类型不合法");
        };
    }

    private String writeSnapshot(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // 快照序列化失败必须让举报提交失败：
            // 一条没有快照的举报等于「证据只有举报人一句话」，客服无从核实
            throw new BusinessException("举报信息保存失败，请稍后重试");
        }
    }

    private Map<String, Object> readSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            // 历史脏数据解析失败时返回空对象，不能让整个列表页 500
            return new LinkedHashMap<>();
        }
    }

    private String firstImage(Long productId) {
        List<ProductImage> images = productImageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSort)
                        .orderByAsc(ProductImage::getId)
                        .last("limit 1"));
        return images.isEmpty() ? null : images.get(0).getImageUrl();
    }

    // ==================== VO 装配 ====================

    /**
     * 把「原始字段」补成「带文案与快照的展示字段」。
     * <p>
     * 举报的展示字段有三类来源，必须分开处理：
     * <ul>
     *   <li><b>枚举文案</b>（类型/原因/状态/动作名与配色）：由码值直接映射，零成本；</li>
     *   <li><b>快照派生</b>（对象标题/封面/是否已消失）：从 target_snapshot JSON 里取，
     *       这是「举报那一刻」的对象信息，即使对象已被下架或删除也还在；</li>
     *   <li><b>图片数组</b>：库里是逗号分隔字符串，对外要数组。</li>
     * </ul>
     * 这三样<b>只在这里算一次</b>：详情走 {@link #toVO(Report)}，管理端列表走
     * JOIN 投影后同样要补——两条路径如果各写一份，迟早有一份漏掉某个字段，
     * 表现为「列表页类型列是空白、点进详情却又是好的」这种极难定位的问题。
     */
    private void fillDerived(ReportVO vo) {
        vo.setReportTypeLabel(ReportType.label(vo.getReportType()));
        vo.setReasonLabel(ReportReason.label(vo.getReason()));
        vo.setStatusLabel(ReportStatus.label(vo.getStatus()));
        vo.setStatusColor(ReportStatus.color(vo.getStatus()));
        vo.setHandleActionLabel(ReportHandleAction.label(vo.getHandleAction()));
        vo.setHoursAgo(hoursAgo(vo.getCreateTime()));
        vo.setImages(splitImages(vo.getImagesRaw()));

        Map<String, Object> snapshot = readSnapshot(vo.getTargetSnapshotRaw());
        // 评价快照里的 images 是 JSON 数组字符串，前端要的是数组。
        // 在这里统一转成 List，前端就不用各写一遍 JSON.parse，
        // 也不会出现「按逗号 split 却拿到带引号的路径」这种错位
        if ("review".equals(snapshot.get("kind")) && snapshot.get("images") != null) {
            snapshot.put("images", parseImageArray(str(snapshot.get("images"))));
        }
        vo.setSnapshot(snapshot);
        vo.setTargetTitle(snapshotTitle(snapshot));
        vo.setTargetCover(snapshotCover(snapshot));
        // 三类快照的对象名分别落在 title / nickname / content，
        // 全都没有说明对象在提交时就是空的（历史脏数据）
        vo.setTargetMissing(!snapshot.containsKey("title")
                && !snapshot.containsKey("nickname")
                && !snapshot.containsKey("content"));
    }

    private ReportVO toVO(Report report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setReportType(report.getReportType());
        vo.setTargetId(report.getTargetId());
        vo.setTargetUserId(report.getTargetUserId());
        vo.setReason(report.getReason());
        vo.setContent(report.getContent());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleResult(report.getHandleResult());
        vo.setHandleAction(report.getHandleAction());
        vo.setHandleTime(report.getHandleTime());
        vo.setAcceptTime(report.getAcceptTime());
        vo.setReporterId(report.getReporterId());
        vo.setCreateTime(report.getCreateTime());
        // 原始字段先塞进 VO 的内部槽位，再交给统一的装配逻辑
        vo.setTargetSnapshotRaw(report.getTargetSnapshot());
        vo.setImagesRaw(report.getImages());

        fillDerived(vo);
        fillTargetSummary(vo);
        vo.setHandleHours(handleHours(report));
        return vo;
    }

    /**
     * 补上报证人昵称/头像、处理人昵称、被举报人当前状态。
     * <p>
     * 用批量查询而非逐条 select：管理端列表一页 10 条、每条要查 3 个用户，
     * 逐条查就是 30 次往返，在管理端也能感知到卡顿。
     * <p>
     * 昵称如果 SQL 已经 JOIN 出来了就不再查——管理端列表走的是 JOIN 投影，
     * 这里再查一遍纯属白费。
     */
    private void fillTargetSummary(ReportVO vo) {
        if (vo.getReporterId() != null && !StringUtils.hasText(vo.getReporterName())) {
            SysUser reporter = sysUserMapper.selectById(vo.getReporterId());
            if (reporter != null) {
                vo.setReporterName(reporter.getNickname());
                vo.setReporterAvatar(reporter.getAvatar());
            }
        }
        if (vo.getHandlerId() != null) {
            SysUser handler = sysUserMapper.selectById(vo.getHandlerId());
            if (handler != null) {
                vo.setHandlerName(handler.getNickname());
            }
        }
        if (vo.getTargetUserId() == null) {
            return;
        }
        SysUser target = sysUserMapper.selectById(vo.getTargetUserId());
        if (target == null) {
            vo.setTargetUserName("已注销用户");
            vo.setTargetUserStatus("账号已注销");
            return;
        }
        if (!StringUtils.hasText(vo.getTargetUserName())) {
            vo.setTargetUserName(target.getNickname());
        }
        vo.setTargetUserStatus(Integer.valueOf(1).equals(target.getStatus()) ? "已封禁" : "正常");
    }

    private String snapshotTitle(Map<String, Object> snapshot) {
        Object kind = snapshot.get("kind");
        if ("product".equals(kind)) {
            return str(snapshot.get("title"));
        }
        if ("user".equals(kind)) {
            return str(snapshot.get("nickname"));
        }
        if ("review".equals(kind)) {
            String content = str(snapshot.get("content"));
            if (content == null) {
                return null;
            }
            return content.length() > 30 ? content.substring(0, 30) + "…" : content;
        }
        return null;
    }

    private String snapshotCover(Map<String, Object> snapshot) {
        Object kind = snapshot.get("kind");
        if ("product".equals(kind)) {
            return str(snapshot.get("cover"));
        }
        if ("user".equals(kind)) {
            return str(snapshot.get("avatar"));
        }
        if ("review".equals(kind)) {
            // 评价配图取第一张，让客服一眼看到刷评证据。
            // 注意 review.images 存的是 JSON 数组字符串（["/uploads/a.png"]），
            // 不是逗号分隔串——直接按逗号拆会把整个 "[]" 当成一个文件名
            List<String> images = parseImageArray(str(snapshot.get("images")));
            return images.isEmpty() ? null : images.get(0);
        }
        return null;
    }

    /**
     * 解析图片串：兼容「JSON 数组」与「逗号分隔」两种历史格式。
     * <p>
     * 评价的 images 一直是 JSON 数组，而举报表自己的 images 是逗号分隔，
     * 快照里两者都可能出现。写成只认一种格式的话，
     * 另一种就会解析出 {@code ["[]"]} 这种假图片路径。
     */
    private List<String> parseImageArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
                return parsed.stream()
                        .filter(StringUtils::hasText)
                        .toList();
            } catch (Exception e) {
                // 历史脏数据解析不了就当没有图，不能让列表页 500
                return new ArrayList<>();
            }
        }
        return splitImages(text);
    }

    private Double handleHours(Report report) {
        return handleHours(report.getAcceptTime(), report.getCreateTime(), report.getHandleTime());
    }

    /**
     * 从接单到处理完成的时长（小时）。
     * <p>
     * 起点用 accept_time 而不是 create_time：排队时间不是处理时间，
     * 混在一起会让「平均处理时长」这个指标反过来逼客服抢单而不是把单做干净。
     * 未接单直接判定的历史数据用 create_time 兜底。
     */
    private Double handleHours(LocalDateTime acceptTime, LocalDateTime createTime, LocalDateTime handleTime) {
        if (handleTime == null) {
            return null;
        }
        LocalDateTime start = acceptTime == null ? createTime : acceptTime;
        if (start == null) {
            return null;
        }
        return round(Duration.between(start, handleTime).toMinutes() / 60.0 * 100) / 100.0;
    }

    private Double handleHours(ReportVO vo) {
        return handleHours(vo.getAcceptTime(), vo.getCreateTime(), vo.getHandleTime());
    }

    private Long hoursAgo(LocalDateTime time) {
        return time == null ? null : Duration.between(time, LocalDateTime.now()).toHours();
    }

    // ==================== 通知 ====================

    /**
     * 给举报人与被举报人双方发通知。
     * <p>
     * 通知失败只 warn 不回滚：处置动作（下架/封禁/扣分）已经生效并落库，
     * 因为一条提醒写不进去就把它回滚，等于让违规内容重新上架。
     */
    private void notifyBothSides(Report report, int status, String result, String actionSummary) {
        boolean accepted = status == ReportStatus.HANDLED;
        String typeLabel = ReportType.shortLabel(report.getReportType());
        String tail = StringUtils.hasText(actionSummary) ? "\n已执行处置：" + actionSummary : "";

        // 通知举报人
        notifyQuietly(report.getReporterId(),
                accepted ? "举报已处理" : "举报未予受理",
                "你举报的" + typeLabel + "（举报#" + report.getId() + "）已完成核实。\n"
                        + "处理结果：" + result + tail, null);

        // 通知被举报人。举报人与被举报人可能是同一个人（互举报），去重避免重复打扰
        if (report.getTargetUserId() != null && !report.getTargetUserId().equals(report.getReporterId())) {
            if (accepted) {
                notifyQuietly(report.getTargetUserId(),
                        "你的" + typeLabel + "被举报并已核实违规",
                        "你发布的" + typeLabel + "被举报，经核实确实存在违规。\n"
                                + "处理结果：" + result + tail
                                + "\n如认为处理有误，可联系平台客服申诉。",
                        report.getReportType() != null && report.getReportType() == ReportType.PRODUCT
                                ? report.getTargetId() : null);
            } else {
                notifyQuietly(report.getTargetUserId(),
                        "举报核实结果：未发现违规",
                        "有用户举报了你的" + typeLabel + "，经平台核实未发现违规，未对你做任何处置。", null);
            }
        }
    }

    private void notifyQuietly(Long userId, String title, String content, Long relatedId) {
        if (userId == null) {
            return;
        }
        try {
            noticeService.notifyReport(userId, title, content, relatedId);
        } catch (Exception e) {
            log.warn("举报通知发送失败 userId={}：{}", userId, e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    /**
 * 过滤证据图片里的空串。
     * <p>
     * 不在这里做「超过3张就截断」：DTO 的 {@code @Size(max=3)} 已经先一步拒绝了超量请求，
     * 静默截断会让举报人以为 5 张都传上去了而客服只看到 3 张。
     */
    private List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (String img : images) {
            if (StringUtils.hasText(img)) {
                list.add(img.trim());
            }
        }
        return list.isEmpty() ? null : list;
    }

    private List<String> splitImages(String images) {
        if (!StringUtils.hasText(images)) {
            return new ArrayList<>();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** 被举报对象的归属人与快照 */
    private record TargetInfo(Long ownerId, Map<String, Object> snapshot) {
    }
}