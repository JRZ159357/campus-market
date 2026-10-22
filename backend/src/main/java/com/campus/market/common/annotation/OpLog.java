// 文件路径: src/main/java/com/campus/market/common/annotation/OpLog.java
package com.campus.market.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。标注在管理端写操作方法上，由 OperationLogAspect 自动落一条日志。
 * <p>
 * 为什么用 AOP 而不是每处手工插日志：
 * <ol>
 *   <li>手工写法一定会漏——「某个写操作忘了记日志」这种漏洞只有出了事才会被发现，
 *       注解 + 切面让"要记录的写操作"变成一个显式的、可 grep 的标记集合，
 *       code review 时能直接搜出来核对；</li>
 *   <li>Service 方法被别处复用时，日志跟着一起生效，
 *       而手工写在 Controller 层的日志在 Service 被其他入口调用时就漏了。</li>
 * </ol>
 * <p>
 * 刻意<b>不</b>在注解里提供 SpEL 表达式参数：detail 字段由
 * {@code OpLogDetail} 接口的默认方法或 aspect 内按类型分派生成，
 * 比让每个方法写 "#{args[0]}" 更容易读，也避免 SpEL 求值失败把主业务带崩。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 业务模块码，如 report / user / product */
    String module();

    /** 动作码，如 accept / handle / ban / update_role */
    String action();

    /** 操作目标ID的来源：0=不记录 1=第1个参数 2=第2个参数 */
    int targetIndex() default 0;

    /**
     * 人能读懂的详情描述，如「处理举报」。
     * <p>
     * 留空时 detail 会退化成「调用 XxxServiceImpl#yyy，参数：…」——
     * 那个字符串对排查有用，但审计页面上没人愿意读
     * {@code 调用 ReportServiceImpl#handle, 参数: 129, {"status":2,...}}。
     * 日志是给人看的证据，不是给编译器看的调用栈。
     */
    String desc() default "";
}