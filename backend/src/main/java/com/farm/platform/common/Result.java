package com.farm.platform.common;

import lombok.Data;

/** 统一响应信封：{code 业务状态码, msg 提示语, data 业务数据}，前端只需判断 code */
@Data
public class Result<T> {

    private Integer code;    // 200成功 400参数错 401未认证 403无权限 500错误
    private String msg;
    private T data;

    /** 成功并带数据（成功时 code/msg 的约定只锁在这一处） */
    public static <T> Result<T> ok(T data) {   // 【统一返回】
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    /** 成功不带数据（删除、状态变更这类），内部就是 ok(null) */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 失败：指定业务状态码与提示语 */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    /** 失败简写：只给提示语时错误码默认 500 */
    public static <T> Result<T> error(String msg) {
        return error(500, msg);
    }
}
