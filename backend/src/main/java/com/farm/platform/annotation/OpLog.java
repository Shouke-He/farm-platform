package com.farm.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志标记：贴在 Controller 方法上，由 {@code aspect/LogAspect} 自动往 {@code operation_log} 表写一笔。
 *
 * <p><b>谁处理：</b>{@code LogAspect#around()} 用 {@code @Around("@annotation(opLog)")} 把方法整个包起来，
 * 方法执行前后各做一件事：执行前记「谁、什么时间、调了哪个接口」，执行后补「成功还是失败、耗时多少」。</p>
 *
 * <p><b>记什么：</b>模块名、操作描述（来自本注解的两个属性）+ 请求 IP / HTTP 方法 / URL
 * + 状态（success / error）+ 错误信息（最多 300 字）+ 耗时（毫秒）+ 操作人 + 时间。</p>
 *
 * <p><b>三个特点：</b></p>
 * <ul>
 *   <li><b>不影响业务</b>：落库走异步，方法抛异常时也先记一条 error 再原样抛出，前端拿到的报错不变；</li>
 *   <li><b>只管 Controller</b>：{@code @Target(METHOD)} 且切点是「方法上有没有这个注解」，
 *       Service 内部互相调用不会重复记录；</li>
 *   <li><b>别贴在高频只读接口上</b>：每次调用都写一行日志，列表接口贴上去会让日志表迅速膨胀。</li>
 * </ul>
 *
 * <p><b>用法示例：</b></p>
 * <pre>{@code
 * @PostMapping
 * @OpLog(module = "任务", operation = "派发任务")
 * public Result<Reminder> create(@RequestBody Reminder reminder) { ... }
 * }</pre>
 *
 * <p>本系统共 35 处使用（账号、任务、提醒、设备、品种、天气、备份等模块）。</p>
 *
 * @see com.farm.platform.aspect.LogAspect
 * @see com.farm.platform.entity.OperationLog
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /**
     * 模块名：日志页按它分类筛选，也是统计看板里「哪个模块操作最多」的依据。
     *
     * <p>取值习惯：2~4 个字，同一模块前后写法保持一致（例如统一用「任务」，不要一会儿「任务」一会儿「农事任务」），
     * 否则日志页的筛选项会分裂成两类。本系统现有取值：账号、任务、提醒、设备、品种、天气、备份、问答等。</p>
     *
     * @return 模块名，如 「任务」「提醒」「账号」
     */
    String module();

    /**
     * 操作描述：写在日志列表「操作」列，说明这一次具体干了什么。
     *
     * <p>建议用「动词 + 对象」的短句（如「派发任务」「重置密码」「手动刷新天气预警」），
     * 不要只写「新增」「修改」——同一个模块下会出现大量同名记录，事后查不出区别。</p>
     *
     * @return 操作描述，如 「派发任务」
     */
    String operation();
}
