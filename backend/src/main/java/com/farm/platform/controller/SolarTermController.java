package com.farm.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.Result;
import com.farm.platform.entity.SolarTerm;
import com.farm.platform.mapper.SolarTermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 二十四节气知识库：只读查询，一个接口。 */
@RestController
@RequestMapping("/api/solar-terms")
@RequiredArgsConstructor
public class SolarTermController {

    private final SolarTermMapper termMapper;

    /** 24 节气全量列表，按 monthDay（形如 01-05 的「月-日」字符串）升序 —— 补零后字符串顺序正好等于日期顺序。 */
    @GetMapping   // 【节气24只读】
    public Result<List<SolarTerm>> list() {
        return Result.ok(termMapper.selectList(new LambdaQueryWrapper<SolarTerm>().orderByAsc(SolarTerm::getMonthDay)));
    }
}
