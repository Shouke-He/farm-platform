-- 品种库水肥参数修正（2026-10-02）
UPDATE plant_species SET soil_min=20, soil_max=40, water_cycle=7, fertilize_cycle=45 WHERE id=180;
-- 百里香：32~57% / 3 天 / 25 天 → 同上，忌涝
UPDATE plant_species SET soil_min=20, soil_max=40, water_cycle=7, fertilize_cycle=45 WHERE id=181;
-- 薰衣草：35~60% / 3 天 / 25 天 → 耐旱怕涝，夏季需控水
UPDATE plant_species SET soil_min=25, soil_max=45, water_cycle=7, fertilize_cycle=40 WHERE id=179;
-- 艾草：38~63% / 3 天 / 25 天 → 深根、耐旱耐贫瘠、适应性极强
UPDATE plant_species SET soil_min=30, soil_max=50, water_cycle=6, fertilize_cycle=30 WHERE id=183;
-- 罗勒：35~60% / 3 天 / 25 天 → 热带香草，喜湿润怕干（比原来更勤浇，方向相反）
UPDATE plant_species SET soil_min=45, soil_max=65, water_cycle=2, fertilize_cycle=20 WHERE id=182;
-- 草莓：40~60% / 7 天 / 45 天（果树模板）→ 草本浅根、喜湿不耐旱，结果期需水需肥多
UPDATE plant_species SET soil_min=55, soil_max=75, water_cycle=2, fertilize_cycle=20 WHERE id=110;
-- 蓝莓：43~63% / 7 天 / 45 天（果树模板）→ 浅须根，喜湿怕涝怕旱，宜少量多次施肥
UPDATE plant_species SET soil_min=50, soil_max=70, water_cycle=3, fertilize_cycle=40 WHERE id=111;
