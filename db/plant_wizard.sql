-- 添加植物向导扩展字段（可重跑：information_schema 判空 + PREPARE，MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS）
SET @db = DATABASE();  -- 取当前库名，下面查 information_schema 时当条件用

SET @c1 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'plant' AND COLUMN_NAME = 'location');  -- 第 1 列：查 plant 表里有没有 location
SET @s1 = IF(@c1 = 0, 'ALTER TABLE plant ADD COLUMN location VARCHAR(10) DEFAULT NULL COMMENT ''种植环境:阳台/窗台/露台/小院/室内/菜地''', 'SELECT 1');  -- 不存在才加：种植环境（阳台/窗台/露台/小院/室内/菜地）
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;  -- 预处理 → 执行 → 释放

SET @c2 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'plant' AND COLUMN_NAME = 'grow_method');  -- 第 2 列：查有没有 grow_method
SET @s2 = IF(@c2 = 0, 'ALTER TABLE plant ADD COLUMN grow_method VARCHAR(10) DEFAULT NULL COMMENT ''种植方式:土培/水培''', 'SELECT 1');  -- 不存在才加：种植方式（土培/水培）
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;  -- 预处理 → 执行 → 释放

SET @c3 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'plant' AND COLUMN_NAME = 'sunlight');  -- 第 3 列：查有没有 sunlight
SET @s3 = IF(@c3 = 0, 'ALTER TABLE plant ADD COLUMN sunlight VARCHAR(16) DEFAULT NULL COMMENT ''光照:少于2小时/2-4小时/4-6小时/6小时以上''', 'SELECT 1');  -- 不存在才加：每天光照时长的档位
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;  -- 预处理 → 执行 → 释放

SET @c4 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'plant' AND COLUMN_NAME = 'container');  -- 第 4 列：查有没有 container
SET @s4 = IF(@c4 = 0, 'ALTER TABLE plant ADD COLUMN container VARCHAR(16) DEFAULT NULL COMMENT ''容器:小盆/中盆/大盆/水培盒/还没准备''', 'SELECT 1');  -- 不存在才加：容器规格（小盆/中盆/大盆/水培盒/还没准备）
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;  -- 预处理 → 执行 → 释放

SET @c5 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'plant' AND COLUMN_NAME = 'stage_name');  -- 第 5 列：查有没有 stage_name
SET @s5 = IF(@c5 = 0, 'ALTER TABLE plant ADD COLUMN stage_name VARCHAR(16) DEFAULT NULL COMMENT ''生长阶段:取品种growth_stages的stage名''', 'SELECT 1');  -- 不存在才加：当前生长阶段名，取值来自品种 growth_stages 里的 stage
PREPARE st5 FROM @s5; EXECUTE st5; DEALLOCATE PREPARE st5;  -- 预处理 → 执行 → 释放
