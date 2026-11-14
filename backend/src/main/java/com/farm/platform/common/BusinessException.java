package com.farm.platform.common;

import lombok.Getter;

/** 业务异常：带错误码，抛出来由 GlobalExceptionHandler 统一翻译成 {code,msg,data} */
@Getter
public class BusinessException extends RuntimeException {

    // 错误码：400 参数错、401 未认证、403 无权限、500 系统错
    private final int code;

    /** 只给提示语，错误码默认 500 */
    public BusinessException(String message) {   // 【业务异常】
        super(message);
        this.code = 500;
    }

    /** 显式指定错误码（如 401、403） */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
