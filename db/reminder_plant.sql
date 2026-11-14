-- 农事任务关联种植物：reminder 表加 plant_id（可空，NULL=通用任务）
SET @exists = (  -- 查 reminder 表里有没有 plant_id 这一列
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'farm_db' AND TABLE_NAME = 'reminder' AND COLUMN_NAME = 'plant_id'  -- 查询条件：farm_db 库、reminder 表、plant_id 列
);
SET @sql = IF(@exists = 0,  -- 拼出要执行的语句：列不存在就加列，否则执行空操作
  'ALTER TABLE reminder ADD COLUMN plant_id BIGINT DEFAULT NULL COMMENT ''关联的种植物 id（NULL=通用任务）''',  -- 加列语句（字符串里两个连续单引号是 SQL 的转义写法）
  'SELECT ''plant_id already exists''');  -- 列已存在时执行的一句提示性查询
PREPARE stmt FROM @sql;  -- 预处理
EXECUTE stmt;  -- 执行
DEALLOCATE PREPARE stmt;  -- 释放
