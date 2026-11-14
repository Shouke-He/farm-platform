package com.farm.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.platform.entity.Crop;

/** crop 表（作物字典）的数据访问接口，继承 MyBatis-Plus 的 BaseMapper 后自动拥有增删改查，无需手写 SQL。 */
public interface CropMapper extends BaseMapper<Crop> {
}
