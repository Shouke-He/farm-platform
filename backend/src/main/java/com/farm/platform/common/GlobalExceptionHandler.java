package com.farm.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理：统一翻译成 {code,msg,data}；HTTP 状态码一律 200，业务成败看 code （【追问·返回200】） */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：采用异常自带的 code 与提示语 */
    @ExceptionHandler(BusinessException.class)   // 【异常收口】
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：取 @NotBlank(message=...) 那句话，固定 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)   // 参数校验失败
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        return Result.error(400, fe == null ? "参数校验失败" : fe.getDefaultMessage());
    }

    /** 兜底：完整堆栈只进日志，前端只拿一句话，固定 500 */
    @ExceptionHandler(Exception.class)   // 兜底异常
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常：" + e.getMessage());
    }
}
