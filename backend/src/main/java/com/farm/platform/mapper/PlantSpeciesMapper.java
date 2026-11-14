package com.farm.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.platform.entity.PlantSpecies;
import org.apache.ibatis.annotations.Mapper;

/** plant_species 表（盆栽植物品种库）的数据访问接口，继承 MyBatis-Plus 的 BaseMapper 后自动拥有增删改查。 */
@Mapper
public interface PlantSpeciesMapper extends BaseMapper<PlantSpecies> {
}
