package com.farm.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.platform.entity.Plant;
import org.apache.ibatis.annotations.Mapper;

/** plant 表（用户盆栽/种植物）的数据访问接口，继承 MyBatis-Plus 的 BaseMapper 后自动拥有增删改查。 */
@Mapper
public interface PlantMapper extends BaseMapper<Plant> {
}
