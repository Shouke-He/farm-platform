-- 农业方向演示种植物数据（可重跑）
USE farm_db;  -- 切到目标库

-- 清理旧盆栽数据（品种库已全量更换，旧数据语义错乱）
DELETE FROM plant WHERE user_id = 7;           -- testuser1 旧盆栽
DELETE FROM plant WHERE user_id NOT IN (4, 6); -- 其余孤儿数据

-- SN-102 修正归属（历史遗留：绑到已不存在的 user_id=5 → worker2 是 id=6）
UPDATE device SET user_id = 6 WHERE device_no = 'SN-102' AND user_id = 5;  -- 只改「确实是孤儿」的那一行：条件里带上 user_id = 5

-- worker1（id=4）：茉莉花，2026-07-25 种植（演示：幼苗期）
INSERT INTO plant (user_id, name, species_id, photo, plant_date, device_id, remark)  -- 插 worker1 的茉莉花：species_id=16 就是 species_v2.sql 里茉莉花的 id
SELECT 4, '横县茉莉花', 16, NULL, '2026-07-25', 6, '广西横县茉莉花种植演示'  -- 一行常量：user_id=4、品种 16、设备 id=6（SN-101）、种植日期 2026-07-25
WHERE NOT EXISTS (SELECT 1 FROM plant WHERE user_id = 4);  -- 幂等关键：worker1 名下已经有种植物就跳过，不重复插

-- worker2（id=6）：番茄，2026-06-20 种植（演示：开花坐果期）
INSERT INTO plant (user_id, name, species_id, photo, plant_date, device_id, remark)  -- 插 worker2 的大棚番茄：species_id=5 就是品种库里番茄的 id
SELECT 6, '大棚番茄', 5, NULL, '2026-06-20', 7, '大棚番茄种植演示'  -- 一行常量：user_id=6、品种 5、设备 id=7（SN-102）、种植日期 2026-06-20
WHERE NOT EXISTS (SELECT 1 FROM plant WHERE user_id = 6);  -- 幂等关键：worker2 名下已经有种植物就跳过

-- 核对
SELECT p.id, u.username, p.name, s.name AS species, s.category, p.plant_date, d.device_no  -- 核对：把种植物连同主人、品种、绑定设备一起查出来看
FROM plant p JOIN sys_user u ON p.user_id = u.id  -- 内连接用户表，拿到用户名
JOIN plant_species s ON p.species_id = s.id  -- 内连接品种库，拿到品种名和分类
LEFT JOIN device d ON p.device_id = d.id;  -- 左连接设备表：没绑设备的种植物也要能查出来，所以用 LEFT JOIN
