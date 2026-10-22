// 文件路径: src/main/java/com/campus/market/aspect/OperationLogAspect.java
package com.campus.market.aspect;

import com.campus.market.common.annotation.OpLog;
import com.campus.market.service.OperationLogService;
import com.campus.market.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * 操作日志切面。
 * <p>
 * 用 {@code @AfterReturning} 而不是 {@code @Around}：
 * Around 需要自己 try/finally 转发 proceed()，一旦漏掉就把业务方法吞了；
 * AfterReturning 由容器负责调用转发，写法更短也更不容易出错。
 * <p>
 * detail 文案由这里统一生成，而不是让注解写 SpEL：
 * 业务方法的参数形态各不相同（userId / dto / operatorId），靠 SpEL 逐个适配会得到一堆
 * {@code #{args[2]}} 这种可读性极差的字符串，而且 SpEL 求值失败会连带影响切面执行。
 * 这里直接把参数用 Jackson 序列化成 JSON 片段，
 * 排障时能一眼看到「封禁时传了 status=1、reason=虚假宣传」。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /** detail 字段最长 500 字符，参数序列化结果也要跟着截断 */
    private static final int MAX_DETAIL_LENGTH = 500;

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Pointcut("@annotation(com.campus.market.common.annotation.OpLog)")
    public void opLogPointcut() {
    }

    @AfterReturning(pointcut = "opLogPointcut()")
    public void record(JoinPoint joinPoint) {
        try {
            OpLog opLog = resolveAnnotation(joinPoint);
            if (opLog == null) {
                return;
            }
            Long targetId = resolveTargetId(joinPoint, opLog.targetIndex());
            String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                    + '#' + joinPoint.getSignature().getName();
            String detail = buildDetail(opLog, method, joinPoint.getArgs(), targetId);
            // 操作人从上下文取而不是从参数里挖：
            // Service 方法签名里 operatorId 的位置不固定，从上下文拿才可靠
            Long operatorId = UserContext.getUserIdOrNull();
            operationLogService.record(operatorId, opLog.module(), opLog.action(), targetId, detail);
        } catch (Exception e) {
            // 切面自身绝不能影响业务：operationLogService.record 之外的意外
            // （如参数序列化失败）也必须被兜住，否则会抛到调用链上打断主流程
            log.warn("操作日志切面异常 method={}：{}", joinPoint.getSignature().getName(), e.getMessage());
        }
    }

    /**
     * 从被拦截方法的签名上取 {@link OpLog}。
     * <p>
     * 不能写成 advice 的第二个参数：那样 AspectJ 会尝试从切点表达式里绑定这个形参，
     * 而 {@code @annotation(xxx)} 切点绑定不了它，启动时直接抛
     * {@code IllegalArgumentException: formal unbound in pointcut}。
     */
    private OpLog resolveAnnotation(JoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        return signature.getMethod().getAnnotation(OpLog.class);
    }

    private Long resolveTargetId(JoinPoint joinPoint, int index) {
        if (index <= 0) {
            return null;
        }
        Object[] args = joinPoint.getArgs();
        if (index > args.length) {
            return null;
        }
        Object arg = args[index - 1];
        return arg instanceof Number number ? number.longValue() : null;
    }

    /**
     * 组装详情文案。
     * <p>
     * 优先用注解里的 {@code desc()}：「接单举报 #129；参数：129」这种审计人员能读懂；
     * 注解没写 desc 时退回方法签名 + 参数，至少保证有原始证据可查。
     * 参数始终保留，因为「做对了什么」只有业务描述说了算，
     * 「具体改成了什么值」必须靠参数快照兜底。
     */
    private String buildDetail(OpLog opLog, String method, Object[] args, Long targetId) {
        StringBuilder sb = new StringBuilder();
        String desc = opLog.desc();
        if (desc != null && !desc.isBlank()) {
            sb.append(desc.trim());
            if (targetId != null) {
                sb.append(" #").append(targetId);
            }
            sb.append("；参数：").append(describeArgs(args));
        } else {
            sb.append("调用 ").append(method).append("，参数：").append(describeArgs(args));
            if (targetId != null) {
                sb.append("，目标ID=").append(targetId);
            }
        }
        return sb.length() > MAX_DETAIL_LENGTH ? sb.substring(0, MAX_DETAIL_LENGTH) : sb.toString();
    }

    private String describeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "无";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Object arg : args) {
            joiner.add(describeArg(arg));
        }
        return joiner.toString();
    }

    private String describeArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof String s) {
            return "\"" + s + "\"";
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Enum<?>) {
            return String.valueOf(arg);
        }
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            // 序列化不了就退化成类型名，不能因此丢掉整条日志
            return arg.getClass().getSimpleName();
        }
    }
}