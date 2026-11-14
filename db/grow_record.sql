-- 生长状况记录与评估（第十八轮）：可重跑
DROP TABLE IF EXISTS grow_record;
-- 新结构：一条记录 = 对某株植物的一次生长测量（株高/叶色/长势/照片），外加一条评估结论。
CREATE TABLE grow_record (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  plant_id    BIGINT       NOT NULL COMMENT '种植物 id',
  record_time DATETIME     NOT NULL COMMENT '记录时间',
  height_cm   DECIMAL(6,1) NULL COMMENT '株高 cm',
  leaf_color  VARCHAR(10)  NULL COMMENT '叶色：嫩绿/正常绿/发黄/干枯',
  vigor       VARCHAR(10)  NULL COMMENT '长势：旺盛/正常/缓慢/停滞',
  photo       VARCHAR(255) NULL COMMENT '照片 URL',
  assessment  VARCHAR(10)  NULL COMMENT '评估：正常/低于预期/优于预期',
  remark      VARCHAR(500) NULL COMMENT '备注',
  create_time DATETIME     NOT NULL COMMENT '创建时间',
  KEY idx_plant_time (plant_id, record_time)  -- 联合索引：按「种植物 + 记录时间」取数（详情页的时间线就按时间排序）
);

-- 品种参考株高列（幂等添加：已存在则跳过，可重跑）
DROP PROCEDURE IF EXISTS add_ref_height_cols;  -- 先删同名存储过程，保证脚本可以反复执行
DELIMITER $$  -- 临时把语句结束符从 ; 改成 $$，这样过程体里面的分号不会被当成语句结尾
CREATE PROCEDURE add_ref_height_cols()  -- 定义一个只干「加两列」这件事的过程
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS  -- 第一次判断：参考株高下限列在不在
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plant_species' AND COLUMN_NAME = 'ref_height_min') THEN  -- 查询条件用 DATABASE() 取当前库名，避免写死库名
    ALTER TABLE plant_species ADD COLUMN ref_height_min DECIMAL(6,1) NULL COMMENT '参考株高下限 cm';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS  -- 第二次判断：参考株高上限列在不在
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plant_species' AND COLUMN_NAME = 'ref_height_max') THEN  -- 同样不存在才加
    ALTER TABLE plant_species ADD COLUMN ref_height_max DECIMAL(6,1) NULL COMMENT '参考株高上限 cm';
  END IF;  -- 第二次判断结束
END$$  -- 过程体结束（用 $$ 收尾，而不是分号）
DELIMITER ;  -- 恢复 ; 为默认的语句结束符
CALL add_ref_height_cols();  -- 调用这个过程，真正执行加列
DROP PROCEDURE add_ref_height_cols;  -- 用完即删，不在库里留下这个存储过程

-- 常用品种参考株高（cm；西瓜/香蕉/荔枝/火龙果等株高语义不适用的品种不填，株高不参与评估）
UPDATE plant_species SET ref_height_min=30,  ref_height_max=90   WHERE name='茉莉花';
UPDATE plant_species SET ref_height_min=20,  ref_height_max=100  WHERE name='绿萝';
UPDATE plant_species SET ref_height_min=15,  ref_height_max=60   WHERE name='薄荷';
UPDATE plant_species SET ref_height_min=5,   ref_height_max=25   WHERE name='多肉植物';
UPDATE plant_species SET ref_height_min=30,  ref_height_max=150  WHERE name='月季';
UPDATE plant_species SET ref_height_min=80,  ref_height_max=120  WHERE name='水稻';
UPDATE plant_species SET ref_height_min=200, ref_height_max=300  WHERE name='玉米';
UPDATE plant_species SET ref_height_min=30,  ref_height_max=60   WHERE name='花生';
UPDATE plant_species SET ref_height_min=50,  ref_height_max=150  WHERE name='番茄';
UPDATE plant_species SET ref_height_min=40,  ref_height_max=90   WHERE name='辣椒';
UPDATE plant_species SET ref_height_min=150, ref_height_max=250  WHERE name='黄瓜';
UPDATE plant_species SET ref_height_min=200, ref_height_max=300  WHERE name='豆角';
UPDATE plant_species SET ref_height_min=20,  ref_height_max=50   WHERE name='红薯';
UPDATE plant_species SET ref_height_min=200, ref_height_max=400  WHERE name='甘蔗';
UPDATE plant_species SET ref_height_min=100, ref_height_max=300  WHERE name='柑橘';
