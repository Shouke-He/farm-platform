-- 新增重复方式 interval（每 N 天）—— 2026-10-04

DROP PROCEDURE IF EXISTS add_interval_days;
DELIMITER //
CREATE PROCEDURE add_interval_days()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reminder' AND COLUMN_NAME = 'interval_days') THEN
    ALTER TABLE `reminder` ADD COLUMN `interval_days` int NULL COMMENT '每 N 天（repeat_type=interval 时用）' AFTER `repeat_type`;
  END IF;
END //
DELIMITER ;
CALL add_interval_days();
DROP PROCEDURE add_interval_days;

ALTER TABLE `reminder` MODIFY COLUMN `repeat_type` VARCHAR(16) DEFAULT 'none' COMMENT '重复:none/daily/weekly/monthly/yearly/interval(每N天)';

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reminder' AND COLUMN_NAME IN ('repeat_type','interval_days');