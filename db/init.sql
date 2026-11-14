-- 农事作业智能提醒与记录平台 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS farm_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;  -- 建库；utf8mb4 才能存 emoji，unicode_ci 是不区分大小写的排序规则
USE farm_db;  -- 切到该库：后面的建表和插数据都作用在 farm_db 上

-- 允许任意顺序重建表（外键约束）
SET FOREIGN_KEY_CHECKS = 0;  -- 先关掉外键检查，DROP/CREATE 表就不受外键依赖顺序限制

-- ---------- 1. 角色表 ----------
DROP TABLE IF EXISTS sys_role;  -- 先删旧表再重建
CREATE TABLE sys_role (
  -- id 自增主键；role_code 唯一且程序按它取角色（新增用户时先按 code 查 id，不写死数字）。
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  role_code   VARCHAR(32)  NOT NULL UNIQUE COMMENT '角色编码',
  role_name   VARCHAR(32)  NOT NULL COMMENT '角色名称',
  remark      VARCHAR(255) DEFAULT NULL,  -- 角色说明
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP  -- 创建时间，默认取当前时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ---------- 2. 用户表 ----------
DROP TABLE IF EXISTS sys_user;
-- 用户表：一个登录账号一行。登录名唯一，密码列存的是加密后的字符串（不是明文）。
CREATE TABLE sys_user (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  username    VARCHAR(32)  NOT NULL UNIQUE COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  real_name   VARCHAR(32)  NOT NULL COMMENT '真实姓名',
  phone       VARCHAR(20)  DEFAULT NULL,  -- 联系电话
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  status      TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  KEY idx_role (role_id),  -- 索引：按角色查用户
  CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(id)  -- 外键：role_id 指向 sys_role.id
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------- 3. 作物表 ----------
DROP TABLE IF EXISTS crop;
-- 作物表：大田作物的品种档案（水稻、小麦……），给「作物生长阶段模板」做父表。
CREATE TABLE crop (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  name        VARCHAR(32)  NOT NULL COMMENT '作物名称',
  category    VARCHAR(32)  DEFAULT NULL COMMENT '类别(粮食/蔬菜/油料等)',
  growth_days INT          DEFAULT NULL COMMENT '全生长周期天数',
  season      VARCHAR(32)  DEFAULT NULL COMMENT '适宜种植季节',
  remark      VARCHAR(255) DEFAULT NULL,  -- 备注
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP  -- 创建时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作物表';

-- ---------- 4. 作物生长阶段模板表 ----------
DROP TABLE IF EXISTS crop_stage;
-- 作物生长阶段模板表：一种作物按「种植后第几天到第几天」拆成若干阶段，每个阶段带农事建议。
CREATE TABLE crop_stage (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  crop_id   BIGINT       NOT NULL,  -- 所属作物 id
  stage_name VARCHAR(32) NOT NULL COMMENT '阶段名(播种期/苗期/分蘖期等)',
  day_start INT          NOT NULL COMMENT '种植后天数起',
  day_end   INT          NOT NULL COMMENT '种植后天数止',
  advice    VARCHAR(500) DEFAULT NULL COMMENT '该阶段农事建议',
  KEY idx_crop (crop_id),  -- 索引：按作物查它的阶段
  CONSTRAINT fk_stage_crop FOREIGN KEY (crop_id) REFERENCES crop(id)  -- 外键：crop_id 指向 crop.id
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作物生长阶段模板';

-- ---------- 5. 二十四节气知识库 ----------
DROP TABLE IF EXISTS solar_term;
-- 二十四节气知识库：每个节气存公历日期（约数）、应时农事建议和一句农谚。
CREATE TABLE solar_term (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  term_name VARCHAR(16) NOT NULL UNIQUE COMMENT '节气名',
  month_day VARCHAR(8)  NOT NULL COMMENT '公历日期(约,如04-05)',
  advice   VARCHAR(500) DEFAULT NULL COMMENT '应时农事建议',
  proverb  VARCHAR(200) DEFAULT NULL COMMENT '农谚'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二十四节气知识库';

-- ---------- 6. 提醒表 ----------
DROP TABLE IF EXISTS reminder;
-- 农事提醒表：全系统的「待办」都落在这一张表里 —— 人工新建的提醒、天气预警、
CREATE TABLE reminder (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  title       VARCHAR(100) NOT NULL COMMENT '提醒标题',
  type        VARCHAR(16)  NOT NULL COMMENT '来源:custom农事任务/plant_advice种植建议/plant_pest虫害预防/sensor环境告警/weather/solar',
  content     VARCHAR(500) DEFAULT NULL COMMENT '提醒内容',
  user_id     BIGINT       DEFAULT NULL COMMENT '接收人(空=全体)',
  land_id     BIGINT       DEFAULT NULL COMMENT '关联地块',
  crop_id     BIGINT       DEFAULT NULL COMMENT '关联作物',
  remind_time DATETIME     NOT NULL COMMENT '提醒时间',
  repeat_type VARCHAR(16)  DEFAULT 'none' COMMENT '重复:none/daily/weekly/monthly/yearly/interval(每N天)',
  interval_days int         DEFAULT NULL COMMENT '每 N 天（repeat_type=interval 时用；2026-10-04 加）',
  priority    VARCHAR(16)  DEFAULT 'normal' COMMENT 'normal/important/urgent',
  status      VARCHAR(16)  DEFAULT 'pending' COMMENT 'pending/done/cancelled',
  source      VARCHAR(32)  DEFAULT 'custom' COMMENT '自动生成来源描述',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  KEY idx_time (remind_time),  -- 索引：按提醒时间查（今日清单按时间排序取数）
  KEY idx_user (user_id)  -- 索引：按接收人查
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农事提醒表';

-- ---------- 7. 作物生长记录表 ----------
DROP TABLE IF EXISTS grow_record;
-- 作物生长记录表（早期「地块 + 作物」版结构）：一次田间生长测量。
CREATE TABLE grow_record (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  land_id     BIGINT       NOT NULL,  -- 地块 id
  crop_id     BIGINT       NOT NULL,  -- 作物 id
  record_date DATE         NOT NULL COMMENT '记录日期',
  height      DECIMAL(6,2) DEFAULT NULL COMMENT '株高(cm)',
  leaf_color  VARCHAR(32)  DEFAULT NULL COMMENT '叶色',
  tiller_count INT         DEFAULT NULL COMMENT '分蘖数',
  pest_status VARCHAR(200) DEFAULT NULL COMMENT '病虫害情况',
  remark      VARCHAR(255) DEFAULT NULL,  -- 备注
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
  KEY idx_land (land_id)  -- 索引：按地块查记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作物生长记录';

-- ============ 初始数据 ============

-- 下面开始是初始数据（种子数据）。每条 INSERT 都显式写了主键 id，方便彼此引用。
INSERT INTO sys_role (id, role_code, role_name, remark) VALUES
(1, 'ADMIN',        '超级管理员', '系统全局参数配置与账号管理'),
(2, 'FARM_ADMIN',   '农场管理员', '系统全部功能权限'),
(3, 'AGRONOMIST',   '农技人员',   '创建和管理农事提醒、查看记录与统计'),
(4, 'WORKER',       '普通作业人员', '接收提醒、填写农事记录和上传附件');

-- 演示账号（密码均为 123456，BCrypt 加密）
INSERT INTO sys_user (id, username, password, real_name, phone, role_id) VALUES
(1, 'admin',   '$2b$10$Y40NmuvbQ8TxIjOXNTMDye9aTzI5PabnkclRKazJZLaXV7Bqs5sgC', '系统管理员', '13800000001', 1),
(2, 'farm1',   '$2b$10$Y40NmuvbQ8TxIjOXNTMDye9aTzI5PabnkclRKazJZLaXV7Bqs5sgC', '李农场',     '13800000002', 2),
(3, 'agro1',   '$2b$10$Y40NmuvbQ8TxIjOXNTMDye9aTzI5PabnkclRKazJZLaXV7Bqs5sgC', '王技术员',   '13800000003', 3),
(4, 'worker1', '$2b$10$Y40NmuvbQ8TxIjOXNTMDye9aTzI5PabnkclRKazJZLaXV7Bqs5sgC', '张师傅',     '13800000004', 4);

-- 作物
INSERT INTO crop (id, name, category, growth_days, season, remark) VALUES
(1, '水稻', '粮食', 120, '春/夏', '华南双季稻区'),
(2, '小麦', '粮食', 200, '秋播', '冬小麦'),
(3, '玉米', '粮食', 110, '春/夏', ''),
(4, '花生', '油料', 130, '春播', ''),
(5, '白菜', '蔬菜', 70,  '秋播', '大白菜');

-- 作物生长阶段模板（以水稻为例，另给玉米/小麦简化模板）
INSERT INTO crop_stage (crop_id, stage_name, day_start, day_end, advice) VALUES
(1, '播种期',    1,  10,  '整地做畦，浸种催芽后播种，保持田间湿润'),
(1, '秧苗期',   11,  35,  '注意保温保湿，防治立枯病，适时追施苗肥'),
(1, '分蘖期',   36,  70,  '浅水勤灌促分蘖，追施分蘖肥，防治稻飞虱'),
(1, '拔节孕穗期',71,  95,  '保持水层，追施穗肥，防治稻瘟病和螟虫'),
(1, '抽穗扬花期',96, 110,  '浅水灌溉，注意防治穗颈瘟，保持通风'),
(1, '成熟期',  111, 120,  '排水晒田，适时收割，防止倒伏'),
(2, '播种期',    1,  15,  '足墒播种，播深3-5厘米'),
(2, '出苗分蘖期',16,  90,  '冬前控旺促壮，适时浇冻水'),
(2, '拔节孕穗期',91, 150,  '追施拔节肥，防治纹枯病'),
(2, '抽穗灌浆期',151, 180, '浇好灌浆水，防治蚜虫和赤霉病'),
(2, '成熟期',  181, 200,  '适时收获，防止穗发芽'),
(3, '播种期',    1,  15,  '整地施肥，适期播种'),
(3, '苗期',     16,  40,  '及时间苗定苗，防治地下害虫'),
(3, '拔节抽穗期',41,  80,  '重施穗肥，注意防旱'),
(3, '灌浆成熟期',81, 110,  '适时收获，防霉变');

-- 二十四节气知识库（农谚+建议）
INSERT INTO solar_term (term_name, month_day, advice, proverb) VALUES
('立春', '02-04', '土地解冻，开始春耕备耕，整地施肥，检修农机具', '一年之计在于春，一日之计在于晨'),
('雨水', '02-19', '小麦返青，注意浇返青水、追返青肥，防春旱', '雨水有雨庄稼好，大春小春一片宝'),
('惊蛰', '03-05', '万物复苏，冬眠虫害开始活动，及时防虫治病', '惊蛰不耙地，好比蒸馍走了气'),
('春分', '03-20', '昼夜平分，早稻播种育秧，春玉米播种', '春分前后怕春霜，一见春霜麦苗伤'),
('清明', '04-04', '气温回升，抓紧春耕春播，棉花、花生播种', '清明前后，种瓜点豆'),
('谷雨', '04-20', '雨生百谷，移栽水稻秧苗，防治病虫害', '谷雨前后，种瓜点豆；谷雨不种花，心里像巴巴'),
('立夏', '05-05', '夏熟作物进入生长后期，水稻分蘖期管理', '立夏看夏，小满见三新'),
('小满', '05-21', '小麦灌浆，注意浇好灌浆水，防治干热风', '小满不满，芒种不管'),
('芒种', '06-05', '忙收忙种，夏收夏种开始，抢收小麦抢种夏玉米', '芒种忙种，可收可种'),
('夏至', '06-21', '水稻中耕除草，防伏旱，注意蓄水保墒', '夏至不过不热，冬至不过不寒'),
('小暑', '07-07', '早稻收割，晚稻插秧，注意防涝', '小暑大暑，灌死老鼠'),
('大暑', '07-22', '高温伏旱，加强田间水分管理，防中暑防病虫', '大暑小暑，上蒸下煮'),
('立秋', '08-07', '秋收作物后期管理，追肥防早衰', '立秋无雨空欢喜，万物结实一半收'),
('处暑', '08-23', '晚稻田间管理，防病虫，秋菜播种', '处暑雨，粒粒皆是米'),
('白露', '09-07', '秋收准备，棉花采摘，晚稻抽穗', '白露白迷迷，秋分稻秀齐'),
('秋分', '09-23', '秋收秋种大忙，水稻收割，小麦备播', '秋分早，霜降迟，寒露种麦正当时'),
('寒露', '10-08', '小麦播种，晚稻收割，防霜冻', '寒露不摘棉，霜打莫怨天'),
('霜降', '10-23', '秋收扫尾，储藏红薯、白菜收获', '霜降见霜，米谷满仓'),
('立冬', '11-07', '冬小麦浇冻水，果树冬剪，农田水利建设', '立冬补冬，补嘴空'),
('小雪', '11-22', '大棚蔬菜保温防寒，储粮防霉', '小雪雪满天，来年必丰年'),
('大雪', '12-07', '冬修水利，积肥造肥，防寒防冻', '大雪纷纷落，明年吃馍馍'),
('冬至', '12-22', '农田水利整修，追施腊肥', '冬至不冷，夏至不热'),
('小寒', '01-05', '防寒防冻，积肥造肥，加强牲畜保暖', '小寒大寒，冷成冰团'),
('大寒', '01-20', '备耕备种，检修农具，积肥送肥', '大寒年年有，不在三九在四九');

-- 演示提醒（自定义+节气，含已办与待办）
INSERT INTO reminder (title, type, content, user_id, land_id, crop_id, remind_time, repeat_type, priority, status, source) VALUES
('早稻分蘖期追肥提醒', 'growth', '水稻分蘖期应追施分蘖肥，建议每亩追施尿素8-10公斤', 4, 1, 1, '2026-08-12 08:00:00', 'none', 'important', 'pending', '生长周期自动推算'),
('芒种节气农事提醒', 'solar', '芒种到，抢收小麦抢种夏玉米，注意天气变化', NULL, NULL, NULL, '2026-08-15 06:00:00', 'yearly', 'normal', 'pending', '节气知识库'),
('雨后打药提醒', 'custom', '连续降雨后及时排水，天晴后补打杀菌剂', 4, 1, 1, '2026-08-10 09:00:00', 'none', 'normal', 'done', '人工创建'),
('每周农具保养', 'custom', '每周五对旋耕机进行例行保养检查', NULL, NULL, NULL, '2026-08-14 17:00:00', 'weekly', 'normal', 'pending', '人工创建');

-- 作物生长记录示例
INSERT INTO grow_record (land_id, crop_id, record_date, height, leaf_color, tiller_count, pest_status, remark) VALUES
(1, 1, '2026-08-05', 28.50, '浓绿', 12, '无', '长势良好'),
(1, 1, '2026-08-10', 32.00, '浓绿', 14, '未见病虫害', '分蘖旺盛'),
(4, 3, '2026-08-08', 165.00, '绿色', NULL, '局部发现玉米螟', '已上报管理员');

-- 操作日志表
CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自增
  user_id BIGINT NULL COMMENT '操作用户ID',
  username VARCHAR(50) NULL COMMENT '账号',
  real_name VARCHAR(50) NULL COMMENT '姓名',
  role_name VARCHAR(30) NULL COMMENT '角色',
  module VARCHAR(30) NULL COMMENT '模块',
  operation VARCHAR(100) NULL COMMENT '操作描述',
  method VARCHAR(10) NULL COMMENT 'HTTP方法',
  url VARCHAR(255) NULL COMMENT '请求地址',
  ip VARCHAR(50) NULL COMMENT '客户端IP',
  duration_ms BIGINT NULL COMMENT '耗时(毫秒)',
  status VARCHAR(10) NULL COMMENT 'success/error',
  error_msg VARCHAR(500) NULL COMMENT '错误信息',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_user (user_id), KEY idx_time (create_time), KEY idx_module (module)  -- 三个索引：按用户 / 按操作时间 / 按模块查日志
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
SET FOREIGN_KEY_CHECKS = 1;  -- 全部语句执行完，把外键检查重新打开
