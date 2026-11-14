package com.farm.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.Result;
import com.farm.platform.entity.Crop;
import com.farm.platform.entity.CropStage;
import com.farm.platform.mapper.CropMapper;
import com.farm.platform.mapper.CropStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 作物（大田作物）及其生长阶段模板的管理：作物本身的增删改查 + 某作物的阶段模板列表。 */
@RestController
@RequestMapping("/api/crops")
@RequiredArgsConstructor
public class CropController {

    private final CropMapper cropMapper;
    private final CropStageMapper stageMapper;

    /** 作物列表（按 id 升序）。 */
    @GetMapping
    public Result<List<Crop>> list() {
        return Result.ok(cropMapper.selectList(new LambdaQueryWrapper<Crop>().orderByAsc(Crop::getId)));
    }

    /** 新增作物：把请求体整行插入 crop 表，再把带主键的对象回给前端。 */
    @PostMapping
    public Result<Crop> create(@RequestBody Crop crop) {
        cropMapper.insert(crop);
        return Result.ok(crop);
    }

    /** 修改作物：按请求体里的主键更新 crop 表。 */
    @PutMapping
    public Result<Void> update(@RequestBody Crop crop) {
        cropMapper.updateById(crop);
        return Result.ok();
    }

    /** 删除作物：按路径上的 id 删 crop 表里的那一行。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cropMapper.deleteById(id);
        return Result.ok();
    }

    /** 某作物的生长阶段模板列表：按起始天数 dayStart 升序（比如「播种 0 天」「苗期 10 天」「开花 45 天」）。 */
    @GetMapping("/{id}/stages")
    public Result<List<CropStage>> stages(@PathVariable Long id) {
        return Result.ok(stageMapper.selectList(
                new LambdaQueryWrapper<CropStage>().eq(CropStage::getCropId, id).orderByAsc(CropStage::getDayStart)));
    }

    /** 给指定作物加一个生长阶段模板：路径上的作物 id 写进 stage.cropId 后再插入。 */
    @PostMapping("/{id}/stages")
    public Result<CropStage> addStage(@PathVariable Long id, @RequestBody CropStage stage) {
        stage.setCropId(id);
        stageMapper.insert(stage);
        return Result.ok(stage);
    }

    /** 删除一条生长阶段模板（注意路径是 /stages/{stageId}，不带作物 id）。 */
    @DeleteMapping("/stages/{stageId}")
    public Result<Void> deleteStage(@PathVariable Long stageId) {
        stageMapper.deleteById(stageId);
        return Result.ok();
    }
}
