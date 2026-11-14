-- ============================================================
-- ⚠️ 2026-10-04 起已归档，**不要执行**（放在 db/archive/ 就是因为它跑不通了）：
--   ① 它往 farm_task / input_record 插数据，而这两张表 2026-08-19 已从活库 DROP
--      （地块概念取消、任务并进 reminder 表 type='custom'）；
--   ② 它写死 role_id = 4，而 sys_role 现在只有 1=ADMIN、2=USER，执行会撞外键 fk_user_role。
--   留在仓库里只为追溯当年演示数据长什么样。现行脚本清单见 db/README.md。
-- ============================================================
-- 演示数据补充（2026-08-10）
-- 目标：让看板/日历/提醒在演示时数据丰满、时间线真实
-- 包含：worker2 李师傅、7-8 月全时间线任务（含逾期/即将到期演示）、
--       农资投入、生长记录、节气/自定义提醒
-- 注意：仅执行一次；执行时须加 --default-character-set=utf8mb4
-- ============================================================
USE farm_db;

-- 1. 新增普通作业人员：worker2 李师傅（复用同密码 hash）
INSERT INTO sys_user (username, password, real_name, phone, role_id, status)
VALUES ('worker2', '$2b$10$Y40NmuvbQ8TxIjOXNTMDye9aTzI5PabnkclRKazJZLaXV7Bqs5sgC', '李师傅', '13800002002', 4, 1);

-- 2. 任务时间线（今天 = 2026-08-10）
-- 7 月历史任务（已完成）
INSERT INTO farm_task (task_no, title, task_type, land_id, content, assigner_id, assignee_id, deadline, status, summary, work_hours, complete_time, create_time) VALUES
('RW-20260705', '早稻田整地', '整地', 1, '旋耕两遍，清除前茬稻桩，施足底肥', 2, 4, '2026-07-05 18:00:00', 'completed', '旋耕两遍，田面平整，底肥已施', 6.0, '2026-07-05 17:20:00', '2026-07-03 09:00:00'),
('RW-20260712', '早稻直播播种', '播种', 1, '选用杂交稻种，直播后镇压保墒', 2, 4, '2026-07-13 18:00:00', 'completed', '直播稻种 40kg，播后镇压，出苗整齐', 8.0, '2026-07-13 11:30:00', '2026-07-10 10:00:00'),
('RW-20260720', '玉米田打药', '打药', 4, '防治玉米螟，傍晚喷施效果最佳', 2, 5, '2026-07-20 18:00:00', 'completed', '氯氰菊酯兑水喷施，虫口密度明显下降', 4.0, '2026-07-20 16:40:00', '2026-07-18 09:30:00'),
('RW-20260728', '大棚浇水', '灌溉', 3, '滴灌补水 2 小时，保持土壤湿润', 2, 5, '2026-07-28 18:00:00', 'completed', '滴灌 2 小时，棚内湿度正常', 2.0, '2026-07-28 10:20:00', '2026-07-26 15:00:00'),
('RW-20260805', '花生地除草', '除草', 5, '人工除草并培土，草龄大的点喷精喹禾灵', 2, 5, '2026-08-02 18:00:00', 'completed', '人工除草+培土，杂草基本清除', 5.0, '2026-08-02 15:10:00', '2026-07-31 10:00:00'),
-- 8 月进行中
('RW-20260806', '早稻田灌水', '灌溉', 1, '分蘖期保持浅水层，视墒情补水', 3, 4, '2026-08-08 18:00:00', 'doing', '已补水 2 次，保持 3-5cm 浅水层', 2.0, NULL, '2026-08-05 09:00:00'),
('RW-20260810', '玉米田追肥', '施肥', 4, '大喇叭口期追施尿素，培土掩埋', 1, 5, '2026-08-12 18:00:00', 'doing', '已领办，尿素撒施并覆土', 3.0, NULL, '2026-08-09 16:00:00'),
-- 8 月已完成
('RW-20260808', '大棚白菜移栽', '播种', 3, '白菜苗移栽，行距 35cm 株距 25cm', 3, 5, '2026-08-10 18:00:00', 'completed', '移栽 1200 株，浇定根水', 6.5, '2026-08-09 17:40:00', '2026-08-07 09:30:00'),
-- 逾期演示：deadline 已过仍未领办 → 动态显示"逾期"
('RW-20260809', '早稻田晒田', '灌溉', 1, '分蘖末期排水晒田 5-7 天，控蘖促根', 3, 4, '2026-08-09 18:00:00', 'pending', NULL, NULL, NULL, '2026-08-07 14:00:00'),
-- 即将到期演示：24h 内到期 → WebSocket 到期提醒
('RW-20260811', '花生控旺打药', '打药', 5, '盛花期喷施烯效唑控旺，防倒伏', 1, 4, '2026-08-11 12:00:00', 'pending', NULL, NULL, NULL, '2026-08-10 09:00:00'),
-- 8 月下旬计划任务
('RW-20260812', '玉米虫害巡查', '打药', 4, '重点检查玉米螟二代虫情，发现即上报', 1, 5, '2026-08-12 18:00:00', 'pending', NULL, NULL, NULL, '2026-08-10 10:00:00'),
('RW-20260815', '白菜地补苗', '播种', 3, '检查成活率，缺苗处及时补栽', 3, 4, '2026-08-15 18:00:00', 'pending', NULL, NULL, NULL, '2026-08-12 09:00:00'),
('RW-20260818', '早稻收割准备', '采收', 1, '检查成熟度，检修收割机，晴好天气开镰', 2, 5, '2026-08-18 18:00:00', 'pending', NULL, NULL, NULL, '2026-08-15 09:00:00'),
('RW-20260822', '玉米采收', '采收', 4, '籽粒完熟期采收，及时晾晒入库', 2, 4, '2026-08-22 18:00:00', 'pending', NULL, NULL, NULL, '2026-08-19 09:00:00');

-- 3. 农资投入明细（对应已完成/进行中任务，按 task_no 关联防 id 错位）
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'seed', '杂交稻种', 40.00, 'kg', NULL, '直播用种量' FROM farm_task WHERE task_no = 'RW-20260712';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'pesticide', '氯氰菊酯', 60.00, 'ml', '稀释800倍', '防治玉米螟' FROM farm_task WHERE task_no = 'RW-20260720';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'water', '灌溉用水', 20.00, 'm³', NULL, '滴灌补水' FROM farm_task WHERE task_no = 'RW-20260728';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'pesticide', '精喹禾灵', 100.00, '毫升', '稀释500倍', '点喷大龄杂草' FROM farm_task WHERE task_no = 'RW-20260805';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'water', '灌溉用水', 15.00, 'm³', NULL, '分蘖期浅水层补水' FROM farm_task WHERE task_no = 'RW-20260806';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'fertilizer', '尿素', 35.00, 'kg', NULL, '大喇叭口期追肥' FROM farm_task WHERE task_no = 'RW-20260810';
INSERT INTO input_record (task_id, input_type, name, amount, unit, dilution, remark)
SELECT id, 'seed', '白菜苗', 1200.00, '株', NULL, '定植水浇透' FROM farm_task WHERE task_no = 'RW-20260808';

-- 4. 作物生长记录补充（补全生长趋势曲线）
INSERT INTO grow_record (land_id, crop_id, record_date, height, leaf_color, tiller_count, pest_status, remark) VALUES
(1, 1, '2026-07-15', 18.00, '淡绿', 4, '无', '返青期'),
(1, 1, '2026-07-22', 23.00, '绿', 8, '无', '分蘖加快'),
(1, 1, '2026-07-29', 27.00, '绿', 10, '无', '分蘖盛期'),
(4, 3, '2026-07-26', 118.00, '绿', NULL, '无', '拔节期'),
(4, 3, '2026-08-03', 142.00, '浓绿', NULL, '偶见蚜虫', '已喷施吡虫啉'),
(5, 4, '2026-08-02', 24.00, '绿', NULL, '无', '开花前期'),
(5, 4, '2026-08-09', 30.00, '浓绿', NULL, '无', '盛花期，控旺防倒'),
(3, 5, '2026-08-09', 4.00, '绿', NULL, '无', '移栽缓苗'),
(3, 5, '2026-08-10', 6.00, '绿', NULL, '无', '新叶展开');

-- 5. 提醒补充（节气 + 自定义，覆盖未来两周）
INSERT INTO reminder (title, type, content, user_id, land_id, crop_id, remind_time, repeat_type, priority, status, source) VALUES
('处暑节气农事提醒', 'solar', '晚稻田间管理，防病虫，秋菜播种正当时', NULL, NULL, NULL, '2026-08-23 06:00:00', 'none', 'normal', 'pending', '节气知识库'),
('早稻晒田控水', 'custom', '排水晒田 5-7 天，促进根系下扎', NULL, 1, 1, '2026-08-13 08:00:00', 'none', 'important', 'pending', '人工创建'),
('农机具周保养', 'custom', '收割机、拖拉机例行保养，为早稻收割做准备', NULL, NULL, NULL, '2026-08-16 17:00:00', 'weekly', 'normal', 'pending', '人工创建'),
('玉米蚜虫监测', 'custom', '高温天气蚜虫易爆发，注意监测', NULL, 4, 3, '2026-08-20 08:00:00', 'none', 'important', 'pending', '人工创建'),
('花生控旺药剂准备', 'custom', '准备烯效唑，按天气情况择日喷施', NULL, 5, 4, '2026-08-11 07:30:00', 'none', 'normal', 'pending', '人工创建');
