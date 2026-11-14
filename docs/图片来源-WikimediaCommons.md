# 品种库/病虫害 图片来源（Wikimedia Commons · 2026-09-05 替换）

>
> ⚠️ **2026-09-27 更正：原文写的「无水印」不准确。** Commons 上流传的这批里有一部分是 **Bugwood / Invasive.org 图库**的原图，那批图**自带一个编号水印**（黑底白字，压在图片角落）。把 50 张图的右下角裁出来拼图目视清查，**确认 2 张带编号戳**：
> - `disease/rice-blast.jpg`（稻瘟病）—— 右下角 `5390481`
> - `disease/planthopper.jpg`（稻飞虱）—— 右下角 `UGA5190055`
>

> ~~另有 `species/succulent.jpg`（多肉植物）右下角带相机日期戳 `2012-09-03 14:14:14`~~ → ❌ **2026-09-27 复核推翻**：把该图右下角裁成 35%×20% 单独看，**干干净净，本来就没有水印** —— 那条是拼图目视扫描时的误报。
>
> ✅ **两张真水印已处理**：`rice-blast.jpg` / `planthopper.jpg` 均已**从图片底部裁掉水印所在的条带**（分别裁 62px / 58px，主体无损），四份副本同步（`frontend/public` + `frontend-admin/public` + 两个 `dist`），md5 已核对一致。原件备份在 `%TEMP%\farmdocs\before_watermark/`。
>
> **清查范围说明**：`public/` 下其实有 **388 张**图片（species 208 + disease 180），而上面这次清查只覆盖了 Commons 替换过的「露脸区」约 50 张。**2026-09-27 用户拍板：水印优先级不高，不再扩大清查**，其余图片未逐张核。
>
> **清查方式的局限（别把上面的数量当全量）**：只裁了每张图**右下角 34%×17%** 的区域拼成一张联图目视，所以这是**下界**。水印完全可能压在别处 —— 已知的 `cane-redrot.jpg` 的「耕种帮」就在图片**中部**、本次裁切范围之外（它本来就在「未替换 4 张」名单里）。要彻底清点得逐张看全图。
>
> **为什么要记这一笔**：管理端「操作日志」改动之后，用户端品种库/病虫害页也加上了**点击图片全屏放大**（见 10 文档 / 开发记录.md 十.96）。**放大之后这些水印会看得非常清楚**，演示正好点到这类图可能被注意到。要换图得重新找 Commons 素材，成本不小，先记着。

| 前端文件 | Commons 文件 |
|---|---|
| disease/aphid.jpg | Ants cultivating aphids on the underside of tree leaves, Kluizenbos, Aalst, 2026.jpg |
| disease/bacterial-wilt.jpg | Tomato Bacterial wilt (Pathogen Ralstonia solanacearum) (12091835495).jpg |
| disease/banana-leafspot.jpg | Banana- Black leaf streak (Black sigatoka) - 27276896694.jpg |
| disease/citrus-canker.jpg | Citrus Canker (20151109-APHIS-DB-0052).jpg |
| disease/citrus-huanglongbing.jpg | Citrus Greening (20120120-APHIS-DB-0060).jpg |
| disease/corn-borer.jpg | Ostrinia nubilalis – European Corn Borer Moth (13800001003).jpg |
| disease/downy-mildew.jpg | Plasmopara viticola 305150939.jpg |
| disease/late-blight.jpg | Phytophthora infestans on potato leaf 2.jpg |
| disease/leaf-blight.jpg | Northern corn leaf blight.JPG |
| disease/pepper-anthracnose.jpg | Pepper Disease in Tropical Bonakanda.jpg |
| disease/planthopper.jpg | Nilaparvata lugens - Brown planthopper - UGA5190055.jpg |
| disease/powdery-mildew.jpg | Powdery Mildew in Soyabean leaves.jpg |
| disease/red-spider.jpg | Spider mites on a pepino leaf.png |
| disease/rice-blast.jpg | Rice blast symptoms.jpg |
| disease/rust.jpg | Puccinia recondita f.sp. tritici on Triticum aestivum, Bruine roest op gewone tarwe.jpg |
| disease/sheath-blight.jpg | Diseases of field crops (1941) (20347185203).jpg |
| disease/sugarcane-borer.jpg | A sugar cane borer moth; adult, pupa, anatomical segments an Wellcome V0022477EL.jpg |
| disease/sweetpotato-blackrot.jpg | Java black rot of Okinawan purple sweetpotato (9717715793).jpg |
| disease/tomato-virus.jpg | Yellow curl leaf disease Pj IMG 3162.jpg |
| species/banana.jpg | Banana fruit (musa).jpg |
| species/beans.jpg | Yardlong bean and Carrot.jpg |
| species/cassava.jpg | Cassava plant (Manihot esculenta)1.jpg |
| species/citrus.jpg | Mandarin Oranges (Citrus Reticulata).jpg |
| species/corn.jpg | Corn Zea mays Plant 2000px.jpg |
| species/cucumber.jpg | Starr-150326-0881-Cucumis sativus-flowers and young fruit in Hydroponics greenhouse-Town Sand Island-Midway Atoll |
| species/dragonfruit.jpg | Pitaya ou fruit du dragon 02.jpg |
| species/jasmine.jpg | Jasmine (Jasminum sambac).jpg |
| species/lychee.jpg | Twin lychees (Litchi chinensis)-source.jpg |
| species/mint.jpg | Mint leaves (Mentha spicata).jpg |
| species/peanut.jpg | Arachis hypogaea L. (3870805747).jpg |
| species/pepper.jpg | Starr-120702-7961-Capsicum annuum-habanero peppers fruit-USDA Plant Materials Center-Molokai (25186373815).jpg |
| species/pothos.jpg | Money plant (Epipremnum aureum).jpg |
| species/rice.jpg | Young rice seedlings in muddy rice field, Indonesia.jpg |
| species/rose.jpg | Colours of Earlier Autumn- Rosa Chinensis (Chinese Rose).jpg |
| species/succulent.jpg | Echeveria pilosa Jardin des Plantes.jpg |
| species/sugarcane.jpg | Saccharum officinarum kz04.jpg |
| species/sweetpotato.jpg | Ipomoea batatas Feld nach der Ernte-Feldhygiene-1-Josef Schlghecken.jpg |
| species/tomato.jpg | Tomates en cours de maturation sur un plant de Solanum lycopersicum au Burkina Faso.jpg |
| species/watermelon.jpg | Starr-091122-0099-Citrullus lanatus-fruit in half-Olinda-Maui (24360436384).jpg |
| species/发财树.jpg | Braided Money Tree Plant (Pachira aquatica) 1.jpg |
| species/小麦.jpg | Triticum aestivum - common wheat - Flickr - Matt Lavin.jpg |
| species/橡皮树.jpg | Red sheath around new leaf of rubber plant (Ficus elastica).jpg |
| species/玫瑰.jpg | Rosa Rugosa hedging rose Harlow Museum & Walled Gardens, Essex 02.jpg |
| species/琴叶榕.jpg | Ficus lyrata DSCN4457.jpg |
| species/糯稻.jpg | Rice harvesting by farmers in a wetland field, Bangladesh 2026 02.jpg |
| species/紫稻.jpg | Golden Hour Sunlight over Paddy Field with Dramatic Sky.jpg |
| species/龟背竹.jpg | Monstera deliciosa 001.jpg |

## 未替换（保留原图，仍带素材站水印，共 4 张）

> 📌 注意：**下面这 4 张只是「素材站水印」这一类**。Commons 替换过的 47 张里另有编号戳/日期戳（见开头 2026-09-27 更正），两类要分开看。

- disease/peanut-leafspot.jpg（花生叶斑病）—— Commons 仅有期刊线描图/1903 旧插图，无合适照片
- disease/watermelon-wilt.jpg（西瓜枯萎病）—— Commons 仅有 1899 旧卷图/香蕉枯萎图，无合适照片
- disease/cane-redrot.jpg（甘蔗赤腐病）—— Commons 未检索到
- disease/sheath-blight.jpg（稻纹枯病）—— Commons 无 1:1 图；当前使用 1941《Diseases of field crops》版画（黑白，无水印，亦属 Commons）

> 文件名中含中文者（species/糯稻.jpg 等）前端按名动态加载，新增 jasmine.jpg 已同步 Species.vue 映射（原 jasmine.png 已删除）。
> **2026-09-05 补充：本表 47 张已同步复制到 `frontend-admin/public/`（管理端 5174），两端图片一致；管理端 Species.vue 映射同为 jasmine.jpg。**
