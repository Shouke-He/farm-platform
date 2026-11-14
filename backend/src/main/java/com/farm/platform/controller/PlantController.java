package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.service.CarePlanService;
import com.farm.platform.service.PlantAdviceEngine;
import com.farm.platform.service.PlantService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 个人种植物（盆栽）：列表、详情、新增/修改/删除，另加添加向导第 4 步的建议和手动触发建议引擎。 */
@RestController
@RequestMapping("/api/plants")
@RequiredArgsConstructor
public class PlantController {

    private final PlantService plantService;
    private final PlantMapper plantMapper;
    private final PlantAdviceEngine adviceEngine;
    private final ReminderMapper reminderMapper;
    private final CarePlanService carePlanService;

    /** 我的种植物列表（每株都补了品种、设备名、最新指标、生长期、浇水时机）。 */
    @GetMapping
    public Result<List<Plant>> list() {
        Long roleId = UserContext.getRoleId();
        if (roleId != null && roleId == 1) {
            List<Plant> all = plantMapper.selectList(null);
            // ⚠️ 必须用「补全后的对象」替换掉列表里的元素。getById 是"另查一条再把品种/设备/最新指标
            all.replaceAll(p -> plantService.getById(p.getId()));
            return Result.ok(all);
        }
        return Result.ok(plantService.listByUser(UserContext.getUserId()));
    }

    /** 某株植物的详情（带品种、设备、最新指标、生长期与浇水建议）。 */
    @GetMapping("/{id}")   // 【追问·详情接口】
    public Result<Plant> detail(@PathVariable Long id) {
        Plant p = plantService.getById(id);
        if (p == null) {
            return Result.error(404, "盆栽不存在");
        }
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !p.getUserId().equals(UserContext.getUserId()))) {
            return Result.error(403, "无权查看该盆栽");
        }
        return Result.ok(p);
    }

    /** 添加植物向导第 4 步的「系统建议」：按品种加上用户填的种植环境/方式/光照/容器/阶段现场生成，不落库也不生成提醒。 */
    @GetMapping("/onboard-advice")
    public Result<Map<String, Object>> onboardAdvice(@RequestParam Long speciesId,
                                                     @RequestParam(required = false) String location,
                                                     @RequestParam(required = false) String growMethod,
                                                     @RequestParam(required = false) String sunlight,
                                                     @RequestParam(required = false) String container,
                                                     @RequestParam(required = false) String stageName) {
        return Result.ok(plantService.onboardAdvice(speciesId, location, growMethod, sunlight, container, stageName));
    }

    /** 手动触发一次建议引擎扫描，返回本次新生成的建议条数（平时由 @Scheduled 每 5 分钟自动跑）。 */
    @PostMapping("/advice/scan")
    @OpLog(module = "盆栽", operation = "手动刷新种植建议")
    public Result<Integer> scanAdvice() {
        // 这是全库扫描（不分是谁的盆栽），只该由管理员手动触发；平时的定时扫描不受影响
        Long roleId = UserContext.getRoleId();
        if (roleId == null || roleId != 1) {
            return Result.error(403, "仅管理员可手动触发建议扫描");
        }
        return Result.ok(adviceEngine.scan());
    }   // 【补归属】

    /** 新增一株植物：校验名称和品种必填、几个环境参数的长度上限，再把归属写成当前登录人、创建时间取服务端， */
    @PostMapping
    @OpLog(module = "盆栽", operation = "新增盆栽")
    public Result<Plant> create(@RequestBody Plant p) {
        if (p.getName() == null || p.getName().isBlank() || p.getSpeciesId() == null) {
            return Result.error(400, "盆栽名称和品种不能为空");
        }
        if (p.getLocation() != null && p.getLocation().length() > 10) return Result.error(400, "种植环境参数过长");
        if (p.getGrowMethod() != null && p.getGrowMethod().length() > 10) return Result.error(400, "种植方式参数过长");
        if (p.getSunlight() != null && p.getSunlight().length() > 16) return Result.error(400, "光照参数过长");
        if (p.getContainer() != null && p.getContainer().length() > 16) return Result.error(400, "容器参数过长");
        if (p.getStageName() != null && p.getStageName().length() > 16) return Result.error(400, "生长阶段参数过长");
        p.setId(null);
        p.setUserId(UserContext.getUserId());
        p.setCreateTime(LocalDateTime.now());
        // 新增时若已标注阶段，起始日就是今天 —— 与 update() 的「阶段名变了标今天」同义：
        p.setStageDate(p.getStageName() == null || p.getStageName().isBlank() ? null : LocalDate.now());
        plantMapper.insert(p);
        return Result.ok(plantService.getById(p.getId()));
    }

    /** 修改植物：名称/品种/照片/种植日期/绑定设备/备注/种植环境等字段一起覆盖。 */
    @PutMapping
    @OpLog(module = "盆栽", operation = "修改盆栽")
    public Result<Void> update(@RequestBody Plant p) {
        Plant old = plantMapper.selectById(p.getId());
        if (old == null) {
            return Result.error(404, "盆栽不存在");
        }
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !old.getUserId().equals(UserContext.getUserId()))) {
            return Result.error(403, "无权修改该盆栽");
        }
        old.setName(p.getName());
        old.setSpeciesId(p.getSpeciesId());
        old.setPhoto(p.getPhoto());
        old.setPlantDate(p.getPlantDate());
        old.setDeviceId(p.getDeviceId());
        old.setRemark(p.getRemark());
        old.setLocation(p.getLocation());
        old.setGrowMethod(p.getGrowMethod());
        old.setSunlight(p.getSunlight());
        old.setContainer(p.getContainer());
        boolean stageChanged = !java.util.Objects.equals(old.getStageName(), p.getStageName());
        old.setStageName(p.getStageName());
        // 生长阶段变化：标注当天为阶段第 1 天；清空阶段则回到按种下日期推算
        if (stageChanged) {
            old.setStageDate(p.getStageName() == null || p.getStageName().isBlank() ? null : LocalDate.now());
        } else if (old.getStageName() != null && !old.getStageName().isBlank() && old.getStageDate() == null) {
            // 老数据兼容：已有标注但无起始日期的，以保存当天起算
            old.setStageDate(LocalDate.now());
        }
        plantMapper.updateById(old);
        return Result.ok();
    }

    /** 删除植物：先把引用这株植物的农事任务 plant_id 置空（不留指向已删植物的脏引用），再删植物本身； */
    @DeleteMapping("/{id}")
    @OpLog(module = "盆栽", operation = "删除盆栽")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        Plant old = plantMapper.selectById(id);
        if (old == null) {
            return Result.error(404, "盆栽不存在");
        }
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !old.getUserId().equals(UserContext.getUserId()))) {
            return Result.error(403, "无权删除该盆栽");
        }
        // 解除该植物关联的农事任务引用，防脏引用（reminder.plant_id 指向已删植物）
        reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .eq(Reminder::getPlantId, id)
                .set(Reminder::getPlantId, null));
        plantMapper.deleteById(id);
        return Result.ok();
    }

    /** 一键生成养护计划：把品种档案里的「浇水周期 / 施肥周期」变成这株植物自己的农事任务， */
    @PostMapping("/{id}/care-plan")
    @OpLog(module = "盆栽", operation = "生成养护计划")
    public Result<Map<String, Object>> carePlan(@PathVariable Long id) {
        return Result.ok(carePlanService.generate(id));
    }
}
