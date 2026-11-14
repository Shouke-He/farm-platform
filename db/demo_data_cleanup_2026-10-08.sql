-- 演示数据体检与修正  2026-10-08
USE farm_db;

-- ① 遥测表里 60 行时间戳是 1970-01-01 的坏数据（10 个采样点 × 6 个指标）
DELETE FROM sensor_data WHERE collect_time < '2000-01-01';

-- ② 5 个空壳测试账号：text/张三、text1/李四、text2/王五、e2e_×2
DELETE FROM sys_user WHERE username IN ('text','text1','text2','e2e_21801263','e2e_23337178');

-- ③ 手机号 138-0000-0001 / 138-0000-0004 属于「1111/2222 式」占位数据，换成正常号码
UPDATE sys_user SET phone = '13800001001' WHERE username = 'admin';
UPDATE sys_user SET phone = '13800001002' WHERE username = 'worker1';

-- ④ 盆栽备注里的「种植演示」四个字去掉，改成像是真会写的话
UPDATE plant SET remark = '2026 年 7 月从横县花市买的两年苗，一直放在窗台'
 WHERE id = 2;

-- ⑤ 生长记录修正：
UPDATE grow_record SET record_time = '2026-09-10 20:30:00', create_time = '2026-09-10 20:30:12',
       height_cm = 18.5, leaf_color = '嫩绿', vigor = '旺盛', assessment = '优于预期',
       remark = '打顶后侧枝明显变多'
 WHERE id = 2;
UPDATE grow_record SET height_cm = 22.0, remark = '新叶展开正常' WHERE id = 7;
UPDATE grow_record SET remark = '摘心后新芽长得快' WHERE id = 5;

-- ⑥ 执行记录原本 107 条里只有 4 条写了备注、1 条填了农资，页面上几乎是空的；
UPDATE execution_record SET note = '按建议喷了小苏打水，叶子正反面都喷到了' WHERE id = 125;
UPDATE execution_record SET note = '浇透，盆底出水为止'                    WHERE id = 124;
UPDATE execution_record SET note = '摘掉了几片有锈斑的叶子'                WHERE id = 123;
UPDATE execution_record SET note = '傍晚浇的水，避开中午'                  WHERE id = 122;
UPDATE execution_record SET note = '浇到盆底出水'                          WHERE id = 121;
UPDATE execution_record SET note = '浇透，顺手挪到窗边通风'                WHERE id = 120;
UPDATE execution_record SET note = '撒了复合肥，浇了一遍水',
       inputs = '[{"name":"复合肥","amount":5,"unit":"g"}]'                WHERE id = 119;
UPDATE execution_record SET note = '把表层板结的土松开了'                  WHERE id = 118;
UPDATE execution_record SET note = '补浇了一次水'                          WHERE id = 117;
UPDATE execution_record SET note = '补浇了一次水'                          WHERE id = 116;
UPDATE execution_record SET note = '浇透'                                  WHERE id = 115;
UPDATE execution_record SET note = '浇透'                                  WHERE id = 114;
UPDATE execution_record SET note = '浇水，顺手松了土'                      WHERE id = 113;

-- ⑦ id=112 那条农资单位写成「毫升」—— 复合肥是称重的，应是克
UPDATE execution_record SET inputs = '[{"name":"复合肥","amount":5,"unit":"g"}]' WHERE id = 112;

-- ⑧ 设备名保持不动；SIM-01「公共采集站」是设计内的公共数据源，演示话术见 15 号册
SELECT '修正完成' AS 结果;
SELECT (SELECT COUNT(*) FROM sensor_data WHERE collect_time < '2000-01-01') AS 坏时间戳剩余,
       (SELECT COUNT(*) FROM sys_user) AS 账号数,
       (SELECT COUNT(*) FROM execution_record WHERE note IS NOT NULL) AS 有备注的执行记录,
       (SELECT COUNT(*) FROM grow_record) AS 生长记录数;
