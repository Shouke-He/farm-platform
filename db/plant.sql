-- 第十轮：个人盆栽模块（可重复执行）

USE farm_db;

-- ---------- 1. 植物品种库（20 种常见盆栽） ----------
CREATE TABLE IF NOT EXISTS `plant_species` (
  `id` bigint NOT NULL AUTO_INCREMENT,  -- 主键，自增
  `name` varchar(50) NOT NULL COMMENT '品种名称',
  `emoji` varchar(8) DEFAULT '🪴' COMMENT '图标',
  `soil_min` int DEFAULT 30 COMMENT '土壤湿度适宜下限%',
  `soil_max` int DEFAULT 60 COMMENT '土壤湿度适宜上限%',
  `temp_min` int DEFAULT 15 COMMENT '适宜温度下限℃',
  `temp_max` int DEFAULT 30 COMMENT '适宜温度上限℃',
  `light_level` varchar(10) DEFAULT '散光' COMMENT '光照需求:喜光/散光/耐阴',
  `water_cycle` int DEFAULT 5 COMMENT '浇水周期（天）',
  `fertilize_cycle` int DEFAULT 30 COMMENT '施肥周期（天）',
  `care_tips` varchar(500) DEFAULT NULL COMMENT '养护要点',
  `diseases` varchar(200) DEFAULT NULL COMMENT '易发病虫害（逗号分隔）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  PRIMARY KEY (`id`)  -- 主键
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盆栽植物品种库';

-- 品种库初始数据（按 id 防重，可重跑）
INSERT INTO `plant_species` (`id`, `name`, `emoji`, `soil_min`, `soil_max`, `temp_min`, `temp_max`, `light_level`, `water_cycle`, `fertilize_cycle`, `care_tips`, `diseases`)
SELECT * FROM (  -- 从子查询里造数据，下面每行 UNION ALL 就是一个品种
  SELECT 1 AS id, '绿萝' AS name, '🌿' AS emoji, 40 AS soil_min, 60 AS soil_max, 18 AS temp_min, 30 AS temp_max, '散光' AS light_level, 4 AS water_cycle, 30 AS fertilize_cycle, '喜湿润半阴环境，土壤干透浇透，避免强光直晒，每月施一次稀薄液肥' AS care_tips, '白粉病,叶斑病,红蜘蛛,黄叶病' AS diseases  -- 第一行用 AS 定下列名，后面 UNION ALL 的行按位置对应
  UNION ALL SELECT 2, '多肉植物', '🌵', 10, 25, 15, 28, '喜光', 10, 45, '宁干勿湿！土壤完全干透再浇，夏季高温休眠期停水停肥，冬季保暖防冻', '根腐病,介壳虫,软腐病,缺光徒长'  -- 多肉植物：耐旱型，适宜湿度区间只有 10~25%
  UNION ALL SELECT 3, '君子兰', '🌺', 40, 60, 15, 25, '散光', 6, 30, '怕强光怕积水，盆土见干见湿，春秋换盆分株，夏季需通风降温', '根腐病,软腐病,叶斑病'
  UNION ALL SELECT 4, '吊兰', '🍀', 45, 65, 15, 30, '散光', 4, 30, '适应性强，喜半阴，土壤微润，冬季减少浇水，每两年换盆一次', '叶斑病,介壳虫'
  UNION ALL SELECT 5, '发财树', '🌳', 45, 65, 18, 30, '散光', 6, 30, '耐旱怕涝，盆土干透再浇，冬季 10℃ 以上越冬，忌强光暴晒', '根腐病,叶斑病,介壳虫'
  UNION ALL SELECT 6, '龟背竹', '🌴', 50, 70, 18, 28, '散光', 4, 30, '喜温暖湿润，叶面可喷雾增湿，气生根保持湿润，忌烈日', '叶斑病,炭疽病,日灼病'
  UNION ALL SELECT 7, '幸福树', '🎋', 40, 60, 18, 28, '喜光', 6, 30, '喜光也耐半阴，浇水见干见湿，生长期每月施肥，冬季防冷风', '叶斑病,介壳虫,根腐病'
  UNION ALL SELECT 8, '栀子花', '🌼', 45, 65, 15, 28, '喜光', 4, 20, '喜酸性土壤，缺铁易黄叶，花期前多施磷钾肥，空气干燥时喷水增湿', '黄叶病,红蜘蛛,白粉病'
  UNION ALL SELECT 9, '茉莉花', '🌷', 45, 65, 18, 32, '喜光', 3, 15, '喜阳光充足，花后及时修剪残花，勤施薄肥，冬季室内越冬', '白粉病,红蜘蛛,叶斑病'
  UNION ALL SELECT 10, '薄荷', '🍃', 40, 60, 15, 30, '喜光', 2, 20, '喜阳光喜水，摘心促进分枝，可定期采收嫩叶，生长快需勤换盆', '锈病,白粉病,蚜虫'
  UNION ALL SELECT 11, '铜钱草', '🪙', 50, 80, 15, 30, '喜光', 2, 30, '喜水喜光，半水半土养护最佳，光照不足易徒长，冬季保暖', '烂根,蚜虫,蜗牛'
  UNION ALL SELECT 12, '富贵竹', '🎍', 50, 70, 18, 30, '散光', 7, 60, '水培为主，每周换水一次，忌氯水直用，可晒太阳但勿暴晒', '炭疽病,叶斑病'
  UNION ALL SELECT 13, '文竹', '🍂', 45, 65, 15, 28, '散光', 4, 30, '喜温暖湿润半阴，叶面勤喷水，盆土微润即可，忌浓肥', '叶枯病,红蜘蛛'
  UNION ALL SELECT 14, '兰花（建兰）', '🌾', 50, 70, 15, 28, '散光', 6, 30, '见干见湿，植料透气，夏季遮阴降温，花期控水，忌盆内积水', '炭疽病,根腐病,蓟马'
  UNION ALL SELECT 15, '番茄（阳台）', '🍅', 45, 65, 18, 30, '喜光', 2, 15, '喜光喜肥，结果期多施磷钾肥，需搭架绑蔓，注意通风防病害', '晚疫病,白粉虱,蚜虫,枯萎病'
  UNION ALL SELECT 16, '辣椒（阳台）', '🌶️', 40, 60, 18, 30, '喜光', 2, 15, '喜光喜温，花期控水，结果期勤施肥，防虫防蓟马', '炭疽病,蚜虫,蓟马,螨害'
  UNION ALL SELECT 17, '草莓（阳台）', '🍓', 45, 65, 15, 25, '喜光', 2, 15, '喜光喜凉，土表略干即浇，开花期人工授粉，结果期垫草防烂果', '白粉病,红蜘蛛,灰霉病'
  UNION ALL SELECT 18, '金钱树', '💰', 40, 55, 18, 30, '耐阴', 8, 45, '耐旱耐阴，盆土干透再浇，冬季停肥控水，避免盆内积水烂根', '根腐病,介壳虫'
  UNION ALL SELECT 19, '琴叶榕', '🎹', 50, 70, 18, 30, '散光', 5, 30, '喜温暖湿润，叶片硕大需擦拭除尘，忌频繁挪动，冬季保暖', '叶斑病,红蜘蛛,日灼病'
  UNION ALL SELECT 20, '芦荟', '🪷', 15, 30, 15, 30, '喜光', 12, 60, '极耐旱，土壤干透再浇，喜阳光充足，冬季 5℃ 以上越冬，忌积水', '根腐病,介壳虫,缺光徒长'
) t  -- 子查询必须起别名 t
WHERE NOT EXISTS (SELECT 1 FROM `plant_species` s WHERE s.id = t.id);  -- 幂等关键：按 id 判重，已存在的品种不重复插

-- ---------- 2. 病虫害知识库（20 条） ----------
CREATE TABLE IF NOT EXISTS `plant_disease` (
  `id` bigint NOT NULL AUTO_INCREMENT,  -- 主键，自增
  `name` varchar(50) NOT NULL COMMENT '病虫害名称',
  `plants` varchar(200) DEFAULT NULL COMMENT '易感植物（逗号分隔）',
  `symptom` varchar(500) DEFAULT NULL COMMENT '症状表现',
  `cause` varchar(200) DEFAULT NULL COMMENT '高发诱因',
  `season` varchar(20) DEFAULT NULL COMMENT '高发季节',
  `prevention` varchar(500) DEFAULT NULL COMMENT '防治方法',
  `drug` varchar(200) DEFAULT NULL COMMENT '推荐用药',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  PRIMARY KEY (`id`)  -- 主键
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='病虫害知识库';

-- 20 条常见病虫害的防治资料；同样每行显式写 id + WHERE NOT EXISTS 判重，可重复执行。
INSERT INTO `plant_disease` (`id`, `name`, `plants`, `symptom`, `cause`, `season`, `prevention`, `drug`)
SELECT * FROM (  -- 从子查询里造数据
  SELECT 1 AS id, '白粉病' AS name, '绿萝,薄荷,茉莉,草莓,栀子花' AS plants, '叶片表面覆盖白色粉状霉层，严重时卷曲枯黄' AS symptom, '高温高湿、通风不良、昼夜温差大' AS cause, '夏秋季' AS season, '加强通风、降低湿度、及时摘除病叶' AS prevention, '多菌灵或粉锈宁 500-800 倍液，7 天 1 次连喷 2-3 次' AS drug  -- 第一行用 AS 定下列名
  UNION ALL SELECT 2, '根腐病', '多肉,发财树,金钱树,兰花,君子兰,幸福树', '根部变黑腐烂、叶片发黄萎蔫、拔起有异味', '浇水过频、盆土积水、透气性差', '全年', '控制浇水、更换透气植料、修剪烂根后消毒', '多菌灵 500 倍液浸根 30 分钟，晾干后重新上盆'
  UNION ALL SELECT 3, '叶斑病', '绿萝,吊兰,龟背竹,文竹,发财树,琴叶榕', '叶片出现褐色圆形或不规则斑点，逐渐扩大穿孔', '高湿、通风差、叶面积水', '夏秋季', '摘除病叶、保持通风、避免叶面夜间积水', '百菌清或苯醚甲环唑 800 倍液喷雾'
  UNION ALL SELECT 4, '红蜘蛛', '茉莉,栀子,草莓,琴叶榕,文竹', '叶背有细小红色虫体和蛛网，叶片出现灰白色小点', '干燥高温、通风差、空气湿度过低', '夏季', '增加空气湿度、经常喷水洗叶、发现即隔离', '阿维菌素或哒螨灵 1500 倍液，重点喷叶背'
  UNION ALL SELECT 5, '蚜虫', '薄荷,番茄,辣椒,铜钱草,草莓', '嫩叶背面聚集绿色/黑色小虫，叶片卷曲变形', '温暖潮湿、新梢嫩叶期', '春季', '剪除虫枝、黄板诱杀、水洗叶片', '吡虫啉或苦参碱 1000 倍液喷雾'
  UNION ALL SELECT 6, '介壳虫', '发财树,吊兰,金钱树,芦荟,幸福树', '茎叶附着褐色/白色蜡质小壳，植株长势变弱', '通风差、植株衰弱、盆土过干', '全年', '保持通风、棉签蘸酒精擦拭虫体', '噻嗪酮 800 倍液，7-10 天 1 次连喷 2 次'
  UNION ALL SELECT 7, '炭疽病', '龟背竹,富贵竹,兰花,辣椒,芦荟', '叶片圆形褐色病斑，中央灰白有轮纹，边缘黄晕', '高湿、叶面伤口感染', '夏秋季', '减少淋水、避免叶面受伤、病叶烧毁', '咪鲜胺或炭特灵 1000 倍液喷雾'
  UNION ALL SELECT 8, '晚疫病', '番茄', '叶缘水渍状暗绿斑，果实褐色硬斑，湿度大时叶背白霉', '高湿多雨、温度 18-22℃ 易发', '春秋季', '控水通风、及时摘除病果病叶', '甲霜灵锰锌 500 倍液，7 天 1 次连喷 3 次'
  UNION ALL SELECT 9, '灰霉病', '草莓,多肉,番茄', '花果覆盖灰色霉层，花瓣腐烂，湿度大时蔓延', '低温高湿、光照不足、密度过大', '冬春季', '控湿增光、及时清除病花病果', '腐霉利或异菌脲 800 倍液喷雾'
  UNION ALL SELECT 10, '锈病', '薄荷,月季', '叶背出现橙黄色粉状孢子堆，叶片卷曲早落', '高温高湿、偏施氮肥', '夏秋季', '加强通风、平衡施肥、清除落叶', '三唑酮或烯唑醇 800 倍液喷雾'
  UNION ALL SELECT 11, '黄叶病（缺铁）', '栀子花,茉莉,绿萝', '新叶叶脉间黄化，老叶正常，严重时全叶发白', '盆土偏碱、铁元素缺乏', '全年', '换酸性培养土、每半月浇 1 次矾肥水', '硫酸亚铁 0.2% 溶液浇灌，2 周 1 次'
  UNION ALL SELECT 12, '白粉虱', '番茄,辣椒,茉莉,草莓', '叶背白色小飞虫群集，叶片有蜜露引发煤污病', '温室高温、通风不良', '夏秋季', '黄板诱杀、加强通风、摘除虫叶', '吡虫啉或烯啶虫胺 1500 倍液喷雾'
  UNION ALL SELECT 13, '蓟马', '辣椒,兰花,草莓', '嫩叶皱缩、银灰色条纹、花器畸形掉落', '干旱高温、花期多花处聚集', '夏秋季', '蓝板诱杀、保持湿度、清除杂草', '噻虫嗪或乙基多杀菌素 1000 倍液喷雾'
  UNION ALL SELECT 14, '蜗牛/蛞蝓', '多肉,薄荷,草莓,铜钱草', '叶片被啃食成缺刻，夜间活动见黏液痕迹', '潮湿阴凉、盆底积水', '春季', '盆底垫防虫网、傍晚撒石灰粉、人工捕捉', '四聚乙醛颗粒撒施盆周'
  UNION ALL SELECT 15, '螨害（锈壁虱）', '辣椒,茄子', '叶片背面锈褐色、边缘卷曲，果实表面粗糙', '高温干旱', '夏季', '保持湿度、合理密植', '阿维菌素 1500 倍液，叶背重点喷'
  UNION ALL SELECT 16, '枯萎病', '番茄,辣椒,草莓', '晴天中午整株萎蔫、早晚恢复，茎基部维管束褐变', '连作、根部伤口、高温', '夏季', '轮作换盆、消毒盆土、及时拔除病株', '多菌灵 500 倍液灌根，每 7 天 1 次'
  UNION ALL SELECT 17, '软腐病', '多肉,绿萝,君子兰,兰花', '茎叶水渍状软腐、有臭味、一碰即烂', '伤口感染、高湿闷热', '夏秋季', '操作避免伤口、通风降湿、切除病部', '农用链霉素或氯霉素 1000 倍液涂抹'
  UNION ALL SELECT 18, '缺光徒长', '多肉,芦荟,薄荷', '茎细长、节间拉长、叶片变薄褪色', '光照不足、长期阴暗', '全年', '逐步移至光照充足处、修剪整形', '无需用药，调整光照即可'
  UNION ALL SELECT 19, '日灼病', '多肉,龟背竹,琴叶榕,芦荟', '叶片出现黄白色灼伤斑块、干枯焦边', '强光暴晒、突然移至强光处', '夏季', '逐步增加光照过渡、夏季正午遮阴', '无需用药，移至半阴处恢复'
  UNION ALL SELECT 20, '煤污病', '栀子花,茉莉,薄荷', '叶片表面覆盖黑色煤烟状霉层，影响光合', '蚜虫/介壳虫蜜露引发、通风差', '全年', '先治虫防蜜露、加强通风、清水擦洗叶片', '先治介壳虫/蚜虫，多菌灵 800 倍液辅助'
) t  -- 子查询必须起别名 t
WHERE NOT EXISTS (SELECT 1 FROM `plant_disease` d WHERE d.id = t.id);  -- 幂等关键：按 id 判重

-- ---------- 3. 用户盆栽表 ----------
CREATE TABLE IF NOT EXISTS `plant` (
  `id` bigint NOT NULL AUTO_INCREMENT,  -- 主键，自增
  `user_id` bigint NOT NULL COMMENT '归属用户',
  `name` varchar(50) NOT NULL COMMENT '盆栽名称（如：客厅的绿萝）',
  `species_id` bigint NOT NULL COMMENT '品种ID',
  `photo` varchar(255) DEFAULT NULL COMMENT '照片URL',
  `plant_date` date DEFAULT NULL COMMENT '种植日期',
  `device_id` bigint DEFAULT NULL COMMENT '绑定传感器设备（一套设备可绑多盆）',
  `remark` varchar(200) DEFAULT NULL,  -- 备注
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  PRIMARY KEY (`id`),  -- 主键
  KEY `idx_user` (`user_id`),  -- 索引：按归属用户查他的盆栽
  KEY `idx_species` (`species_id`),  -- 索引：按品种查
  KEY `idx_device` (`device_id`)  -- 索引：按绑定的设备查
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户盆栽';

-- ---------- 4. device 表加归属用户（动态检测列是否存在） ----------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS  -- 数一下 device 表里叫 user_id 的列有几个
            WHERE TABLE_SCHEMA='farm_db' AND TABLE_NAME='device' AND COLUMN_NAME='user_id');  -- 查询条件：farm_db 库、device 表、user_id 列
SET @sql = IF(@col = 0,
  'ALTER TABLE `device` ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT ''归属用户（空=演示设备）'', ADD KEY idx_user (user_id)',  -- 列不存在才执行的加列语句（字符串里两个连续单引号是 SQL 的转义写法）
  'SELECT 1');  -- 列已存在就执行一句无关紧要的 SELECT 1，什么都不改
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;  -- 预处理 → 执行 → 释放，三步走

-- 演示：给个人用户（worker1=4, worker2=5）各配一台个人传感器设备
INSERT INTO `device` (`device_no`, `device_name`, `device_type`, `user_id`, `status`, `remark`)
SELECT * FROM (  -- 从子查询里造数据
  SELECT 'SN-101' AS device_no, '客厅盆栽监测站' AS device_name, 'sensor' AS device_type, 4 AS user_id, 'offline' AS status, '个人盆栽：土壤湿度/气温/空气湿度/光照' AS remark  -- 第一行用 AS 定下列名
  UNION ALL SELECT 'SN-102', '阳台盆栽监测站', 'sensor', 5, 'offline', '个人盆栽：土壤湿度/气温/空气湿度/光照'  -- SN-102 归属 worker2（id=5）
) t  -- 子查询必须起别名 t
WHERE NOT EXISTS (SELECT 1 FROM `device` d WHERE d.device_no = t.device_no);  -- 幂等关键：按 device_no 判重
