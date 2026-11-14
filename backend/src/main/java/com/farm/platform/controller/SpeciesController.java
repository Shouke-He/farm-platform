package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.mapper.PlantSpeciesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 植物品种库：品种字典（番茄、生菜……）及其适宜温湿度、生长阶段定义的查询与维护。 */
@RestController
@RequestMapping("/api/plant-species")
@RequiredArgsConstructor
public class SpeciesController {

    private final PlantSpeciesMapper speciesMapper;

    /** 品种列表：全表返回、不排序（数据量小，前端自己排）；只要求登录，不限角色。 */
    @GetMapping   // 【品种188】
    public Result<List<PlantSpecies>> list() {
        return Result.ok(speciesMapper.selectList(null));
    }

    /** 新增品种：id 置空、createTime 用服务端时间后整行插入。 */
    @PostMapping
    @OpLog(module = "品种库", operation = "新增品种")
    public Result<PlantSpecies> create(@RequestBody PlantSpecies sp) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护品种库");
        }
        sp.setId(null);
        sp.setCreateTime(LocalDateTime.now());
        speciesMapper.insert(sp);
        return Result.ok(sp);
    }

    /** 修改品种：按请求体里的主键更新。 */
    @PutMapping
    @OpLog(module = "品种库", operation = "修改品种")
    public Result<Void> update(@RequestBody PlantSpecies sp) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护品种库");
        }
        speciesMapper.updateById(sp);
        return Result.ok();
    }

    /** 删除品种：按路径上的 id 直删。 */
    @DeleteMapping("/{id}")
    @OpLog(module = "品种库", operation = "删除品种")
    public Result<Void> delete(@PathVariable Long id) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护品种库");
        }
        speciesMapper.deleteById(id);
        return Result.ok();
    }
}
