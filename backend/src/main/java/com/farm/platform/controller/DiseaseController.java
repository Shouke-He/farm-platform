package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.PlantDisease;
import com.farm.platform.mapper.PlantDiseaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 病虫害知识库：症状、防治办法等条目的查询与维护。 */
@RestController
@RequestMapping("/api/plant-diseases")
@RequiredArgsConstructor
public class DiseaseController {

    private final PlantDiseaseMapper diseaseMapper;

    /** 病虫害条目全量列表（不排序，前端自己排）。 */
    @GetMapping   // 【病虫害158】
    public Result<List<PlantDisease>> list() {
        return Result.ok(diseaseMapper.selectList(null));
    }

    /** 新增病虫害条目：id 置空、createTime 用服务端时间，然后整行插入。 */
    @PostMapping
    @OpLog(module = "病虫害库", operation = "新增病虫害")
    public Result<PlantDisease> create(@RequestBody PlantDisease d) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护病虫害库");
        }
        d.setId(null);
        d.setCreateTime(LocalDateTime.now());
        diseaseMapper.insert(d);
        return Result.ok(d);
    }

    /** 修改病虫害条目：按请求体里的主键更新。 */
    @PutMapping
    @OpLog(module = "病虫害库", operation = "修改病虫害")
    public Result<Void> update(@RequestBody PlantDisease d) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护病虫害库");
        }
        diseaseMapper.updateById(d);
        return Result.ok();
    }

    /** 删除病虫害条目：按路径上的 id 直删。 */
    @DeleteMapping("/{id}")
    @OpLog(module = "病虫害库", operation = "删除病虫害")
    public Result<Void> delete(@PathVariable Long id) {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理角色可维护病虫害库");
        }
        diseaseMapper.deleteById(id);
        return Result.ok();
    }
}
