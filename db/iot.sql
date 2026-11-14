-- 第九轮：物联网传感器模块（可重复执行——表存在则跳过）

-- 切到目标库 farm_db
USE farm_db;

-- 设备表
CREATE TABLE IF NOT EXISTS `device` (
  `id` bigint NOT NULL AUTO_INCREMENT,  -- 主键，自增
  `device_no` varchar(32) NOT NULL COMMENT '设备编号（唯一，传感器上报标识）',
  `device_name` varchar(50) NOT NULL COMMENT '设备名称（如：水田1号土壤站）',
  `device_type` varchar(16) DEFAULT 'sensor' COMMENT 'sensor=传感器 / controller=控制器',
  `land_id` bigint DEFAULT NULL COMMENT '绑定地块',
  `status` varchar(10) DEFAULT 'offline' COMMENT 'online=在线 / offline=离线',
  `last_online_time` datetime DEFAULT NULL COMMENT '最近上报时间',
  `remark` varchar(200) DEFAULT NULL,  -- 备注
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  PRIMARY KEY (`id`),  -- 主键
  UNIQUE KEY `uk_device_no` (`device_no`),  -- 唯一索引：设备编号全库唯一（固件里的 DEVICE_NO 就是查这一列）
  KEY `idx_land` (`land_id`)  -- 索引：按地块查设备
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物联网设备（传感器/控制器）';

-- 传感器遥测数据（metric-value 键值对，灵活扩展指标）
CREATE TABLE IF NOT EXISTS `sensor_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,  -- 主键，自增
  `device_id` bigint NOT NULL,  -- 所属设备 id，指向 device.id
  `metric` varchar(20) NOT NULL COMMENT 'soil_humidity土壤湿度/soil_temp土温/air_temp气温/air_humidity空气湿度/light光照/rainfall雨量',
  `value` decimal(8,2) NOT NULL,  -- 指标数值；metric 说明是哪个指标，value 是这个指标的值
  `collect_time` datetime NOT NULL,  -- 采集时间，取自设备上报 JSON 里的 t 字段
  PRIMARY KEY (`id`),  -- 主键
  KEY `idx_device_metric_time` (`device_id`, `metric`, `collect_time`)  -- 联合索引：按「设备 + 指标 + 时间」取数（环境监控页画趋势曲线就走它）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='传感器遥测数据';

-- 初始设备（演示：5 个传感器挂 5 块地；编号即 MQTT topic 标识）
INSERT INTO `device` (`device_no`, `device_name`, `device_type`, `land_id`, `status`, `remark`)
SELECT * FROM (  -- 用 SELECT 造出 5 行设备数据，下面每行 UNION ALL 就是一台设备
  SELECT 'SN-001' AS device_no, '水田1号土壤站' AS device_name, 'sensor', l.id AS land_id, 'offline', '土壤湿度/土温/气温/光照'  -- 第一行用 AS 定下列名，后面 UNION ALL 的行按位置一一对应
  FROM farm_land l WHERE l.id = 1  -- 只取 1 号地块的 id 作为 SN-001 的 land_id
  UNION ALL SELECT 'SN-002', '东区二号田气象站', 'sensor', 2, 'offline', '空气温湿度/光照/雨量'  -- SN-002 绑 2 号地块
  UNION ALL SELECT 'SN-003', '南区大棚环境站', 'sensor', 3, 'offline', '土壤湿度/气温/空气湿度'  -- SN-003 绑 3 号地块
  UNION ALL SELECT 'SN-004', '西区大田监测站', 'sensor', 4, 'offline', '土壤湿度/土温/光照'  -- SN-004 绑 4 号地块
  UNION ALL SELECT 'SN-005', '北区坡地气象站', 'sensor', 5, 'offline', '空气温湿度/雨量/光照'  -- SN-005 绑 5 号地块
) t  -- 子查询必须起别名 t，MySQL 才允许在 WHERE 里引用它
WHERE NOT EXISTS (SELECT 1 FROM `device` d WHERE d.device_no = t.device_no);  -- 幂等关键：只插 device_no 还不存在的设备，已存在的跳过
