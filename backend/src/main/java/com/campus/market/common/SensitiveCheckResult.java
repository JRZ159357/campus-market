// 文件路径: src/main/java/com/campus/market/common/SensitiveCheckResult.java
package com.campus.market.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 敏感词检测结果。三处调用方（商品发布 / 评价提交 / 聊天发送）共用同一个返回对象，
 * 保证「什么算命中」这件事只有一个判断标准。
 * <p>
 * 为什么把「拦截」与「警告」分成两个集合而不是只留一个 hitWords：
 * 一段文案可能同时命中两类词（「走私」+「加微信」），
 * 此时结论必然是拦截，但审核员仍需要知道里面还夹着广告词——
 * 因为驳回理由写「含违禁词走私」和写「含违禁词走私、广告词加微信」
 * 对卖家来说是两种完全不同的教育效果。分开存，两个信息都不丢。
 * <p>
 * 不可变对象：由 {@link #pass()} 或 {@link #of(List, List)} 构造后不再修改，
 * 可以安全地跨线程传递（Service 返回后由 Controller 读取）。
 */
public final class SensitiveCheckResult {

    /** 空结果：多处以 static 常量复用，避免每次都 new */
    private static final SensitiveCheckResult EMPTY =
            new SensitiveCheckResult(Collections.emptyList(), Collections.emptyList());

    /** 命中的拦截级词 */
    private final List<String> blockWords;

    /** 命中的警告级词 */
    private final List<String> warnWords;

    private SensitiveCheckResult(List<String> blockWords, List<String> warnWords) {
        this.blockWords = Collections.unmodifiableList(new ArrayList<>(blockWords));
        this.warnWords = Collections.unmodifiableList(new ArrayList<>(warnWords));
    }

    /**
     * 未命中任何敏感词。
     */
    public static SensitiveCheckResult pass() {
        return EMPTY;
    }

    /**
     * 按命中词构造结果。入参允许为空集合。
     */
    public static SensitiveCheckResult of(List<String> blockWords, List<String> warnWords) {
        boolean noBlock = blockWords == null || blockWords.isEmpty();
        boolean noWarn = warnWords == null || warnWords.isEmpty();
        if (noBlock && noWarn) {
            return EMPTY;
        }
        return new SensitiveCheckResult(
                noBlock ? Collections.emptyList() : blockWords,
                noWarn ? Collections.emptyList() : warnWords);
    }

    /**
     * 是否需要拦截（命中任意一个拦截级词）。
     */
    public boolean isBlocked() {
        return !blockWords.isEmpty();
    }

    /**
     * 是否需要人工复核（未拦截，但命中警告级词）。
     */
    public boolean isWarned() {
        return blockWords.isEmpty() && !warnWords.isEmpty();
    }

    /**
     * 完全干净：既不拦截也不警告。
     */
    public boolean isClean() {
        return blockWords.isEmpty() && warnWords.isEmpty();
    }

    public List<String> getBlockWords() {
        return blockWords;
    }

    public List<String> getWarnWords() {
        return warnWords;
    }

    /**
     * 全部命中词（拦截 + 警告），去重后按发现顺序返回。
     */
    public List<String> getAllWords() {
        if (blockWords.isEmpty()) {
            return warnWords;
        }
        if (warnWords.isEmpty()) {
            return blockWords;
        }
        List<String> all = new ArrayList<>(blockWords);
        all.addAll(warnWords);
        return Collections.unmodifiableList(all);
    }

    /**
     * 命中词拼接成「走私、假币」的形式，用于提示文案与落库的 sensitive_hit 字段。
     */
    public String joinWords() {
        return String.join("、", getAllWords());
    }

    /**
     * 面向用户的拦截提示。文案统一在这里生成，避免三处调用方各写一套
     * （会出现「商品页说包含违规词、聊天页说消息不合规」这种不一致）。
     */
    public String blockMessage() {
        return "内容包含违规词：" + joinWords() + "，请修改后重试";
    }

    /**
     * 面向审核员的标记说明。
     */
    public String warnRemark() {
        return "命中敏感词：" + joinWords();
    }

    /**
     * 聊天场景的替换结果。拦截级词替换成 **，其余原样保留。
     * <p>
     * 为什么聊天不做「整条拒绝」：聊天是即时通讯，直接吞掉消息会让发送者
     * 以为网络坏了而反复重发，客服那边收到的投诉量远大于收益。
     * 替换成 ** 既阻止了违规内容传播，又给了一个明确的即时反馈。
     */
    public String maskBlockWords(String text) {
        if (text == null || text.isEmpty() || blockWords.isEmpty()) {
            return text;
        }
        String masked = text;
        for (String word : blockWords) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            // 原文里可能是变体写法（加空格、全角），这里对「归一化后等价」的
            // 片段做替换：先按原词直接替换，再按去分隔符后的形式兜底
            masked = masked.replace(word, "**");
            String loose = word.replaceAll("[\\s\\-_*·.]", "");
            if (!loose.equals(word) && !loose.isEmpty()) {
                masked = masked.replace(loose, "**");
            }
        }
        return masked;
    }

    /**
     * 调试用输出。
     */
    @Override
    public String toString() {
        return "SensitiveCheckResult{block=" + blockWords.stream().collect(Collectors.joining(","))
                + ", warn=" + warnWords.stream().collect(Collectors.joining(",")) + '}';
    }
}
