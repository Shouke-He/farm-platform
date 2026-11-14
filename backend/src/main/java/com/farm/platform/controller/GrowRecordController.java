package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.entity.GrowRecord;
import com.farm.platform.service.GrowRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 生长状况记录：登记（服务端按品种参考标准自动评估长势）、时间线查询、删除。 */
@RestController
@RequiredArgsConstructor
public class GrowRecordController {

    private final GrowRecordService growRecordService;

    /** 登记一条生长记录：路径上的 id 是哪株植物，服务端算完评估结果再落库。 */
    @PostMapping("/api/plants/{id}/records")
    @OpLog(module = "生长记录", operation = "登记生长记录")
    public Result<GrowRecord> add(@PathVariable Long id, @RequestBody GrowRecord r) {
        return Result.ok(growRecordService.add(id, r));
    }

    /** 某株植物的生长记录时间线，最新在前（注意路径和登记接口一样，靠 HTTP 方法区分读和写）。 */
    @GetMapping("/api/plants/{id}/records")
    public Result<List<GrowRecord>> list(@PathVariable Long id) {
        return Result.ok(growRecordService.listByPlant(id));
    }

    /** 删除一条生长记录（注意路径是 /api/records/{id}，不挂在植物下面）。 */
    @DeleteMapping("/api/records/{id}")
    @OpLog(module = "生长记录", operation = "删除生长记录")
    public Result<Void> delete(@PathVariable Long id) {
        growRecordService.delete(id);
        return Result.ok();
    }
}
