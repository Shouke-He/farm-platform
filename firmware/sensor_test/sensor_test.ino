/*
 * 农事作业智能提醒与记录平台 —— ESP32 传感器测试程序
 * ------------------------------------------------------------------
 * 接线（以你板面丝印为准）：
 *   DHT11  DATA → D4 (GPIO4)        土壤 AOUT → D32 (GPIO32)
 *   光敏   AO   → D33 (GPIO33)      全部 3V3 供电、共地
 * ------------------------------------------------------------------
 * 依赖库（Arduino IDE 库管理器安装）：
 *   "DHT sensor library" by Adafruit（会自动装 Adafruit Unified Sensor）
 * ------------------------------------------------------------------
 * 功能：每 2 秒串口打印 温度 / 湿度 / 土壤raw / 光照raw
 * 用途：验证接线正确 + 标定土壤湿度百分比
 * ------------------------------------------------------------------
 * 【它和 platform_firmware.ino 的分工（别搞混）】
 *   本程序 = 「体检工具」：不连 WiFi、不连 MQTT、不碰后端，只往串口打印读数。两个用途：
 *     ① 刚接完线先烧这个，确认三个传感器都读得到数（DHT11 读失败时会提示检查 D4 接线）；
 *     ② 拿它做土壤标定 —— 先记下探头悬空时的 soil_raw，再把探头泡进水里记一次，
 *        把这两个值填进本文件的 SOIL_AIR_RAW / SOIL_WATER_RAW。
 *   正式固件 = 「上班模式」：引脚和换算公式与这里完全相同，另外加上联网、对时、
 *     每 5 秒一次的 MQTT 上报。两份文件各存了一份标定常量，改了一边记得改另一边。
 *
 * 【MQTT 主题】本程序不联网，没有发布也没有订阅任何主题。
 * 【引脚】D4=GPIO4（数字脚）、D32=GPIO32（ADC1_CH4）、D33=GPIO33（ADC1_CH5），
 *         与正式固件一致；模拟脚只挑 ADC1，是因为开 WiFi 后 ADC2 会失效。
 */
#include <DHT.h>

#define DHT_PIN   4   // DHT11 数据线 → D4  // 数字脚 GPIO4，靠 DHT 库解单总线时序
#define SOIL_PIN  32  // 土壤 AOUT   → D32（ADC1_CH4）  // 模拟脚，读 0~4095 原始值
#define LUX_PIN   33  // 光敏 AO     → D33（ADC1_CH5）  // 模拟脚，读 0~4095 原始值

#define DHT_TYPE  DHT11  // 型号常量：传给 DHT 库的构造函数

DHT dht(DHT_PIN, DHT_TYPE);  // 全局 DHT 对象：绑定引脚与型号，setup() 里 dht.begin() 之后才能读

// ============================================================
// 土壤湿度标定（跑通后填实测值）
// 方法：程序跑起来后，先记录"空气中"的 soil_raw，
//       再把探头泡进水里记录 soil_raw，填到下面两个常量。
// ============================================================
const int SOIL_AIR_RAW   = 3050;  // 实测（2026-08-15）：探头悬空约 3000~3100
const int SOIL_WATER_RAW = 1050;  // 实测（2026-08-15）：探头泡水约 1000~1100

// 土壤 ADC 原始值 → 湿度百分比（0~100），与正式固件里是同一套两头标定算法。
// 读到 SOIL_AIR_RAW(悬空 3050) 算 0%、SOIL_WATER_RAW(泡水 1050) 算 100%，中间线性折算。
// 超范围的值夹到 0 / 100。标定时就是靠它把 raw 值翻译成百分比，判断标定值取得准不准。
float soilToPercent(int raw) {
  if (raw >= SOIL_AIR_RAW)   return 0.0f;
  if (raw <= SOIL_WATER_RAW) return 100.0f;
  return (float)(SOIL_AIR_RAW - raw) / (SOIL_AIR_RAW - SOIL_WATER_RAW) * 100.0f;
}

// 开机只跑一次：开串口(115200) → 打印引脚配置 → 初始化 DHT11。
// 不连 WiFi、不连 MQTT，所以烧录后在没网的环境也能跑，插上 USB 就能看数据。
// 串口监视器的波特率必须和这里一致（115200），不然看到的全是乱码。
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("=== ESP32 Sensor Test (农事平台) ===");
  Serial.printf("DHT11=D%d, SOIL=D%d, LUX=D%d\n", DHT_PIN, SOIL_PIN, LUX_PIN);
  dht.begin();
}

// 主循环：每 2 秒读一次温度 / 湿度 / 土壤 ADC / 光敏 ADC，并打印成一行。
// DHT11 读失败（isnan 为真）时打印提示要求检查 D4 接线，读成功才打印温湿度。
// 土壤既打印原始值 soil_raw 又打印折算后的 soil_pct，方便一眼看出标定是否合理。
// delay(2000) 是为了配合 DHT11 的采样间隔下限 —— 采得太快会读到重复值或直接读失败。
void loop() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  int soilRaw = analogRead(SOIL_PIN);
  int luxRaw  = analogRead(LUX_PIN);

  if (isnan(t) || isnan(h)) {
    Serial.println("DHT11 读取失败！检查 D4 接线");
  } else {
    Serial.printf("temp=%.1fC humi=%.1f%%", t, h);
  }
  Serial.printf("  soil_raw=%d soil_pct=%.0f%%", soilRaw, soilToPercent(soilRaw));
  Serial.printf("  lux_raw=%d\n", luxRaw);

  delay(2000);  // 等 2 秒再采下一轮
}
