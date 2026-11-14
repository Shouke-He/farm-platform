package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.BusinessException;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.GrowRecord;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.mapper.GrowRecordMapper;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 生长记录服务：给盆栽「记一笔」生长状况（对应数据库 grow_record 表）。 */
@Service
@RequiredArgsConstructor
public class GrowRecordService {

    private final GrowRecordMapper growRecordMapper;
    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;

    /** 校验种植物存在且归属（本人或管理角色），返回种植物 */
    private Plant checkPlant(Long plantId) {
        Plant p = plantMapper.selectById(plantId);
        if (p == null) {
            throw new BusinessException("种植物不存在");
        }
        // 管理员(roleId=1)放行；否则要求这盆盆栽的 user_id 就是当前登录人
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !p.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(403, "无权操作该种植物");
        }
        return p;
    }

    /** 登记一条生长记录：先校验盆栽是不是自己的，再由服务端算出评估结论，最后一起落库。 */
    public GrowRecord add(Long plantId, GrowRecord r) {
        Plant p = checkPlant(plantId);
        PlantSpecies sp = p.getSpeciesId() == null ? null : speciesMapper.selectById(p.getSpeciesId());
        // 强制清空前端传的 id，保证一定是新增而不是覆盖已有记录
        r.setId(null);
        r.setPlantId(plantId);
        if (r.getRecordTime() == null) {
            r.setRecordTime(LocalDateTime.now());
        }
        // 评估结论由服务端算，不用前端传的值
        r.setAssessment(evaluate(r, sp));
        r.setCreateTime(LocalDateTime.now());
        growRecordMapper.insert(r);
        return r;
    }

    /** 某盆盆栽的生长记录时间线，按记录时间倒序（最新一条在最上面）。 */
    public List<GrowRecord> listByPlant(Long plantId) {
        checkPlant(plantId);
        return growRecordMapper.selectList(new LambdaQueryWrapper<GrowRecord>()
                .eq(GrowRecord::getPlantId, plantId)
                .orderByDesc(GrowRecord::getRecordTime));
    }

    /** 删除一条生长记录：先按 id 查出记录（拿它的 plant_id），再用 checkPlant 校验这盆盆栽的归属。 */
    public void delete(Long id) {
        GrowRecord r = growRecordMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("生长记录不存在");
        }
        checkPlant(r.getPlantId());
        growRecordMapper.deleteById(id);
    }

    /** 生长状况自动评估，结论只有三个：低于预期 / 优于预期 / 正常。 */
    public static String evaluate(GrowRecord r, PlantSpecies sp) {
        boolean lower = false, upper = false;
        if (r.getLeafColor() != null
                && ("发黄".equals(r.getLeafColor()) || "干枯".equals(r.getLeafColor()))) {
            lower = true;
        }
        if (r.getVigor() != null) {
            if ("旺盛".equals(r.getVigor())) {
                upper = true;
            } else if ("缓慢".equals(r.getVigor()) || "停滞".equals(r.getVigor())) {
                lower = true;
            }
        }
        // 偏低优先：只要有偏低项，不管有没有偏高项，都判低于预期
        if (lower) return "低于预期";
        if (upper) return "优于预期";
        return "正常";
    }
}
