# db/ 目录说明

> 2026-10-04 整理：把「活库脚本」「历史快照」「一次性脚本」分开，目录里只留需要按顺序跑的。
> 2026-10-08 补：`演示数据体检与修正_2026-10-08.sql`（演示数据合规体检，见第三节）。

## 一、目录分工

| 目录 | 放什么 | 要不要跑 |
|---|---|---|
| **`db/`（本层）** | 建库主线的 11 个脚本 + 6 个可选数据/纠错脚本 + 本说明 | ✅ 按下面的顺序跑 |
| `db/backup/` | **改动前快照**（10 个，0.86 MB）。每次动数据前先 mysqldump 出来的原文 | ❌ 只在回滚时用 |
| `db/archive/` | 已完成使命或**已经跑不起来**的一次性脚本 | ❌ 仅作历史参考 |

## 二、建库顺序（照这个顺序从上往下跑）

| # | 脚本 | 干什么 |
|---|---|---|
| 1 | `init.sql` | 建库 + 8 张基础表（角色/账号/作物/生长阶段/节气/提醒/生长记录/操作日志）+ 初始数据 |
| 2 | `iot.sql` | 物联网：`device` + `sensor_data` |
| 3 | `plant.sql` | 种植模块：`plant_species` + `plant_disease` + `plant`，并给 `device` 加 `user_id` |
| 4 | `species_v2.sql` | 品种库换成「15 广西农作物 + 5 盆栽」，加 `category` / `growth_stages` 两列 |
| 5 | `role_v2.sql` | **角色体系从 4 类精简成 2 类**（ADMIN / USER） |
| 6 | `grow_record.sql` | 把 `grow_record` 从「地块+作物」版 DROP 重建成「种植物」版 |
| 7 | `execution_record.sql` | `execution_record` 表（任务执行记录 + 农资/照片） |
| 8 | `reminder_plant.sql` | 给 `reminder` 加 `plant_id`（提醒关联到具体哪株植物） |
| 9 | `plant_wizard.sql` | 给 `plant` 加 5 个「添加向导」字段 |
| 10 | `interval_repeat_2026-10-04.sql` | 给 `reminder` 加 `interval_days`（每 N 天重复），并改 `repeat_type` 注释 |
| 11 | `fix_collation_2026-10-04.sql` | **统一排序规则**：把 `execution_record` / `operation_log` 改成 `utf8mb4_unicode_ci`（改之前跨表字符串 JOIN 会报 1267）。独立可跑，任何时候补都行 |

跑完这 11 个，得到的就是**活库当前的 14 张表**：

```
sys_role  sys_user  crop  crop_stage  solar_term  reminder  grow_record  operation_log
device  sensor_data  plant_species  plant_disease  plant
```

## 三、可选脚本（演示用，按需跑）

| 脚本 | 干什么 |
|---|---|
| `demo_plants_v3.sql` | 演示种植物（worker1 茉莉花、worker2 番茄） |
| `cleanup_demo_data.sql` | 清掉演示设备/账号，只留 admin + worker1 + SN-101 |
| `fix_species_param_2026-10-02.sql` 等 3 个 `fix_*` | 品种参数纠错（已执行完，留着可追溯） |
| `演示数据体检与修正_2026-10-08.sql` | **演示前演示数据合规体检**（依据评审《演示须知》第 7 条「原始数据必需符合字段含义」）：删掉 60 行时间戳是 1970 的坏遥测、删掉 5 个空壳测试账号、把 13800000001 这类占位手机号换成正常号码、去掉盆栽备注里的「种植演示」。**已执行完**；回滚用 `db/backup/backup_2026-10-08_before_data_audit_tables.sql` 与 `..._sensor60.sql` |

## 四、`archive/` 里为什么有东西

| 文件 | 为什么归档 |
|---|---|
| `demo_data.sql` | **已经跑不起来**：它往 `farm_task` / `input_record` 插数据（这两张表 2026-08-19 已从活库删除），并且写死 `role_id = 4`（`sys_role` 现在只有 1=ADMIN、2=USER，会撞外键）。留作历史参考，**不要执行** |

## 五、已知的例外（不影响使用，但要知道）

`init.sql` 里的 `reminder` 表是**早期结构**：多了 `land_id` / `crop_id` 两列（后来的版本不再用），
少了 `city` / `plant_id` / `done_time` 三列（由后续脚本补上）。
所以：**表清单**已经和活库对齐，但**列**仍以「跑完全部 11 个脚本之后」的结果为准。

另外，`init.sql` 与 `execution_record.sql` 现在都**显式写了 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`**。
不靠 `CREATE DATABASE ... COLLATE` 那句带：`IF NOT EXISTS` 在库已存在时**整句被忽略**，那样建表会退回服务器默认排序规则（MySQL 8 = `utf8mb4_0900_ai_ci`），和其余脚本建的表不一致 —— 跨表比字符串会报 `ERROR 1267`。这一点 2026-10-04 踩过一次，已修（`fix_collation_2026-10-04.sql`）。

## 六、跑脚本的注意事项

1. **必须加 `--default-character-set=utf8mb4`**，否则中文会变乱码：
   ```powershell
   Get-Content -LiteralPath db/init.sql -Raw -Encoding UTF8 |
     & "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p*** --default-character-set=utf8mb4
   ```
   （别用 `cmd /c "mysql ... < file"`：路径里有空格会报 `'C:/Program' is not recognized`，而且中文会乱码）
2. `init.sql` 是「先 `DROP TABLE IF EXISTS` 再 `CREATE`」的全量重建写法，**重复执行会清掉旧数据**。
3. 每次动数据之前，先 `mysqldump` 一份丢进 `db/backup/`，命名 `backup_日期_改什么.sql`。
