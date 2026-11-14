package com.farm.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 执行记录创建入参 —— POST /api/executions 的请求体，也就是完成农事任务时弹窗里填的那份表单。 */
@Data
public class ExecutionRequest {
    private Long reminderId;
    /** 缺省时服务端取当前时间 */
    private LocalDateTime executeTime;
    private String note;
    /** 农资明细行，name 为空的过滤掉 */
    private List<InputItem> inputs;
    /** 照片 URL 列表（/uploads/xxx.jpg） */
    private List<String> photos;

    /** 农资明细行：这次农事用了几样东西就填几行（名称 + 数量 + 单位，如「复合肥 200 g」）。 */
    @Data
    public static class InputItem {
        private String name;
        private Double amount;
        private String unit;
    }
}
