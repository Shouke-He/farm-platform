-- 统一排序规则（collation）：把两张「跑偏」的表改回 utf8mb4_unicode_ci

USE farm_db;

-- ① 逐表转换（字符串列本来就是 utf8mb4，这里实际只改排序规则，不改字符集）
ALTER TABLE execution_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE operation_log     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ② 核对：期望 14 张表全部列在 utf8mb4_unicode_ci 一行下
SELECT TABLE_COLLATION, COUNT(*) AS 表数, GROUP_CONCAT(TABLE_NAME ORDER BY TABLE_NAME) AS 表名
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'farm_db'
GROUP BY TABLE_COLLATION;
