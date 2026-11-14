# ESP32 固件烧录说明（Arduino IDE）

> 对应程序：`firmware/sensor_test/sensor_test.ino`（测试程序）
> 更新日期：2026-08-15

---

## 一、装环境（一次性，约 15 分钟）

### 1. 安装 Arduino IDE
- 官网下载：https://www.arduino.cc/en/software （Windows 版，装默认路径即可）

### 2. CH340 驱动（USB 转串口）
- 插上 ESP32 后，设备管理器 → 端口 应出现 `USB-SERIAL CH340 (COMx)`
- 没有的话下载 CH340 驱动安装（驱动精灵可装；或 arduino 官网搜 CH340）

### 3. 装 ESP32 板包（关键一步）
1. Arduino IDE → 文件 → 首选项 → **附加开发板管理器网址** 填入：
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
2. 工具 → 开发板 → 开发板管理器 → 搜索 `esp32` → 安装 **esp32 by Espressif Systems**（约 2GB，等它装完）
3. 安装完会多出大量 ESP32 板型选项

### 4. 装 DHT11 库
- 工具 → 管理库 → 搜索 `DHT sensor library` → 安装 **by Adafruit**（会自动连带安装 Adafruit Unified Sensor）

## 二、烧录测试程序（每次改代码都这 5 步）

1. **打开程序**：`firmware/sensor_test/sensor_test.ino`（.ino 必须放在同名文件夹里，直接打开即可）
2. **选板**：工具 → 开发板 → 搜索并选择 **`ESP32 Dev Module`**
3. **选端口**：工具 → 端口 → 选 `COMx (USB-SERIAL CH340)`
   - 如果端口列表是灰的：换一根能传数据的 USB 线，或先重插
4. **烧录**：点工具栏 → 上传按钮（向右箭头）。等待编译（首次 2~5 分钟）→ 底部出现 `Connecting...` → `Hard resetting...` → 提示上传成功
   - 若卡在 `Connecting...`：按住板上 **BOOT 键**不放，点上传，出现 `Connecting` 时松手
5. **看输出**：工具 → 串口监视器 → 右下角波特率选 **115200** → 每 2 秒打印一行

## 三、预期输出（判断接线正确）

正常应看到（每 2 秒一行）：

```
temp=27.3C humi=58.0%  soil_raw=3482 soil_pct=0%  lux_raw=890
temp=27.2C humi=58.0%  soil_raw=2470 soil_pct=52% lux_raw=910
```

| 项目 | 正常范围 | 异常排查 |
|---|---|---|
| temp | 室温附近（±2°C） | 读到 `NaN` → D4 接线 / DHT11 供电 |
| humi | 30~70% | 同上 |
| soil_raw | 空气 3000+，泡水 1000~2000 | 不变化 → 检查 D32 线 |
| soil_pct | 空气≈0%，盆土 40~70%，水≈100% | 方向反了 → 交换标定值 |
| lux_raw | 亮处高、遮手明显下降 | 不变化 → 检查 D33 线 |

## 四、土壤湿度标定（必做，一分钟）

1. 探头**放空气中**，记下 `soil_raw`（比如 3450）
2. 探头**泡进水里**，记下 `soil_raw`（比如 1250）
3. 打开 `sensor_test.ino`，把两个数字填进：
   ```cpp
   const int SOIL_AIR_RAW   = 3450;  // 你测的空气值
   const int SOIL_WATER_RAW = 1250;  // 你测的泡水值
   ```
4. 重新烧录 → 空气≈0%、泡水≈100%、盆土 40~70% → 标定完成

> 把两个实测值发给我，我会写进正式固件（MQTT 上报到平台），并顺便说明模拟器 JSON 字段对应关系。
