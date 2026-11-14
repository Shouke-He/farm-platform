-- 农事任务执行记录表（可重跑）
CREATE TABLE IF NOT EXISTS execution_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  reminder_id  BIGINT       NOT NULL COMMENT '关联农事任务 reminder.id',
  user_id      BIGINT       NOT NULL COMMENT '执行人',
  execute_time DATETIME     NOT NULL COMMENT '实际执行时间（默认=完成时间）',
  note         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  inputs       TEXT         COMMENT '农资明细 JSON 数组：[{"name":"复合肥","amount":30,"unit":"g"}]',
  photos       TEXT         COMMENT '照片 JSON 数组：["/uploads/xxx.jpg"]',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  KEY idx_reminder (reminder_id),  -- 索引：按任务查它有哪些执行记录
  KEY idx_user_time (user_id, execute_time)  -- 联合索引：按「执行人 + 执行时间」查（个人统计口径走它）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农事任务执行记录';

-- 演示数据：worker1(4) 两条已完成农事任务 + 对应执行记录（可重跑：先删同名）
DELETE r FROM reminder r WHERE r.title IN ('💧 浇水', '🌿 施肥') AND r.user_id = 4;  -- 多表 DELETE 写法：删 r 别名（reminder）里符合条件的行
DELETE e FROM execution_record e WHERE e.user_id = 4;  -- 对应的执行记录也一并删掉，避免留下孤儿数据

-- 第 1 条演示任务：浇水，状态直接置成 done（因为它要配一条执行记录）。
INSERT INTO reminder (title, content, type, user_id, remind_time, repeat_type, priority, status, source, create_time)
VALUES ('💧 浇水', '给茉莉花浇水，浇透土壤', 'custom', 4, NOW(), 'none', 'normal', 'done', '人工创建', NOW());
SET @r1 = LAST_INSERT_ID();  -- 取刚插入那条提醒的 id，存进变量给下面的执行记录用

-- 第 2 条演示任务：施肥，处理方式同上。
INSERT INTO reminder (title, content, type, user_id, remind_time, repeat_type, priority, status, source, create_time)
VALUES ('🌿 施肥', '施复合肥一次，薄肥勤施', 'custom', 4, NOW(), 'none', 'normal', 'done', '人工创建', NOW());
SET @r2 = LAST_INSERT_ID();  -- 同上，取第二条提醒的 id

INSERT INTO execution_record (reminder_id, user_id, execute_time, note, inputs, photos, create_time)  -- 两条执行记录一次插入，分别关联上面两条提醒
VALUES (@r1, 4, NOW(), '浇透，盆底有水渗出', '[{"name":"清水","amount":2,"unit":"L"}]', '[]', NOW()),  -- inputs / photos 存的是 JSON 字符串；photos 为空数组表示这次没传照片
       (@r2, 4, NOW(), '薄肥勤施，避开正午', '[{"name":"复合肥","amount":30,"unit":"g"}]', '[]', NOW());
