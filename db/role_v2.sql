-- 角色精简为两类：管理员（唯一 admin）+ 用户（可重跑）
USE farm_db;  -- 切到目标库

-- 执行完可以顺手核对：角色表只剩 2 行，账号表里每个账号的 role_id 只有 1 或 2。
UPDATE sys_user SET role_id = 2 WHERE role_id IN (2,3,4);  -- 原来的 2/3/4 三类角色统一归并成 2（用户），admin 的 1 不受影响

-- 角色表改为 2 行：1=ADMIN 管理员 / 2=USER 用户
UPDATE sys_role SET role_code='USER', role_name='用户', remark='注册即得：上传种植物/绑设备/看生长期与浇水时机' WHERE id=2;  -- id=2 这一行改造成「用户」角色
UPDATE sys_role SET role_name='管理员', remark='系统唯一管理员：用户/设备/知识库/日志/备份管理' WHERE id=1;  -- id=1 这一行改造成「管理员」角色
DELETE FROM sys_role WHERE id IN (3,4);  -- 删掉不再使用的两个角色（执行前先确认没有账号还挂在 3/4 上）

-- 核对
SELECT id, role_code, role_name FROM sys_role;  -- 核对角色表
SELECT id, username, role_id FROM sys_user ORDER BY id;  -- 核对每个账号归到了哪个角色
