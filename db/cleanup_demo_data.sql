-- 清理演示数据（第十九轮）：删系统演示设备 + 多余演示账号，只留 admin + worker1(张师傅) + SN-101

-- 1. 设备遥测数据（SN-001~005 系统演示 + SN-102/103 孤儿设备；保留 SN-101 客厅盆栽监测站）
DELETE sd FROM sensor_data sd JOIN device d ON d.id = sd.device_id  -- 多表 DELETE 写法：删 sd 别名（sensor_data）里能关联到目标设备的行
WHERE d.device_no IN ('SN-001','SN-002','SN-003','SN-004','SN-005','SN-102','SN-103');  -- 目标设备编号清单（系统演示设备 + 历史遗留的孤儿设备）

-- 2. 平台级传感器告警（按标题前缀匹配；保留 SN-101 客厅盆栽监测站的告警）
DELETE FROM reminder  -- 删传感器告警类的提醒
WHERE source = '物联网传感器' AND (  -- 用「来源 + 标题前缀」双重限定，只删系统演示设备产生的告警，不动用户自己建的任务
  title LIKE '水田1号土壤站%' OR title LIKE '东区二号田气象站%'
  OR title LIKE '南区大棚环境站%' OR title LIKE '西区大田监测站%'
  OR title LIKE '北区坡地气象站%' OR title LIKE '阳台盆栽监测站%');

-- 3. worker2 的种植物与生长记录
DELETE FROM grow_record WHERE plant_id = 3 AND plant_id IN (SELECT id FROM plant WHERE user_id = 6);  -- 先删生长记录（它引用 plant）
DELETE FROM plant WHERE id = 3 AND user_id = 6;  -- 再删种植物本身

-- 4. worker2 / testuser1 的提醒
DELETE FROM reminder WHERE user_id IN (6, 7);  -- worker2（id=6）和 testuser1（id=7）名下的提醒

-- 5. 设备本体（sensor_data 已先删）
DELETE FROM device WHERE device_no IN ('SN-001','SN-002','SN-003','SN-004','SN-005','SN-102','SN-103');  -- 设备本体：它的遥测数据已经在第 1 步删干净了

-- 6. 演示账号（farm1/agro1/worker2/testuser1）
DELETE FROM sys_user WHERE username IN ('farm1','agro1','worker2','testuser1');  -- 演示账号：admin 和 worker1 不在名单里，会被保留
