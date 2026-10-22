// 文件路径: src/main/java/com/campus/market/common/GlobalExceptionHandler.java
package com.campus.market.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。Controller 层不写 try-catch。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.fail("数据已存在，请勿重复提交");
    }

    /**
     * 路由不存在。
     * <p>
     * 必须显式处理：Spring Boot 3 里请求一个没有映射的路径会抛
     * {@code NoResourceFoundException}，而它继承自
     * {@code ServletException} 而非 {@code RuntimeException} 的直接子类，
     * 会一路掉到下面的兜底 handler，被报成「系统繁忙，请稍后重试」——
     * 调用方看到 500 会以为服务端崩了，实际上只是地址写错了。
     * 这类错误必须以 404 的语义如实返回，排查问题时一眼能分清
     * 「我调错了」和「它坏了」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("请求路径不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在: " + e.getResourcePath());
    }

    /** Spring Boot 3 之前的旧式「没有 handler」异常，一并兜住 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException e) {
        log.warn("请求路径不存在: {}", e.getRequestURL());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在: " + e.getRequestURL());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
