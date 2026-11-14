/*
 * 农事作业智能提醒与记录平台 —— ESP32 正式固件 v1.0
 * ------------------------------------------------------------------
 * 接线（板面丝印）：DHT11 DATA→D4, 土壤 AOUT→D32, 光敏 AO→D33
 *                  DHT11 VCC/土壤 VCC/光敏 VCC 并插 3V3，GND 共地
 * 功能：WiFi 连接 → MQTT 上报 farm/device/{no}/telemetry（每 5 秒）
 * JSON 与后端模拟器一致（指标白名单 soil_humidity/soil_temp/
 *      air_temp/air_humidity/light/rssi，rssi=WiFi.RSSI() 信号强度）：
 *   {"t":"2026-08-15 19:30:00","soil_humidity":55.0,"soil_temp":28.4,
 *    "air_temp":32.1,"air_humidity":60.2,"light":890,"rssi":-45}
 * 依赖库：DHT sensor library（已装）、PubSubClient（已装）
 * ------------------------------------------------------------------
 * 使用：改下方"需要配置"区 → 编译上传 → 串口 115200 看日志
 * ------------------------------------------------------------------
 * 【这块板子干什么】 —— 每轮采集即【5秒上报】
 *   本平台感知端的「正式固件」：一块 ESP32 开发板 + 3 个传感器，插在花盆/田里当监测站。
 *   每 5 秒采集一轮 → 拼成一行 JSON → 用 MQTT 发到后端（后端里内嵌了 Moquette Broker）。
 *   后端收到后把每个指标写进 sensor_data 表，并按阈值判断要不要给用户推告警。
 *
 * 【接线表】引脚号取自下面的 #define；板面丝印 D 后面的数字就是 GPIO 编号（D4 即 GPIO4）
 *   模块              模块引脚     接到 ESP32        说明
 *   DHT11 温湿度       DATA        D4  (GPIO4)      数字脚，单总线时序，由 DHT 库解码
 *   土壤湿度传感器     AOUT        D32 (GPIO32)     模拟脚 ADC1_CH4，读 AO 输出电压
 *   光敏电阻模块       AO          D33 (GPIO33)     模拟脚 ADC1_CH5，读 AO 输出电压
 *   三个模块           VCC         3V3              必须用 3V3，不能接 5V
 *   三个模块           GND         GND              共地
 *   为什么模拟脚只挑 D32/D33：这两个脚属于 ADC1。ESP32 一开 WiFi，ADC2 就被无线模块占用、
 *   读数会失效，所以能边联网边采的模拟脚只能从 ADC1（GPIO32~39）里选。
 *
 * 【用什么协议、连哪台服务器】
 *   链路：WiFi（必须 2.4G 频段）→ TCP → MQTT（PubSubClient 库，QoS 0）
 *   Broker：MQTT_HOST 指定的那台电脑上的 1883 端口，也就是跑后端的那台机器（Broker 随后端启动）
 *
 * 【MQTT 主题】本固件只发布，不订阅任何主题
 *   发布（每 5 秒一条，见 loop()）：
 *     farm/device/<DEVICE_NO>/telemetry    遥测 JSON，例如 farm/device/SN-101/telemetry
 *   连接时向 Broker 声明遗嘱 LWT（这是「声明」，不是订阅）：
 *     farm/device/<DEVICE_NO>/lwt          内容 "offline"，QoS 0、不保留
 *     设备拔电或断网时由 Broker 代它发出；后端订阅 farm/device/+/lwt，收到就把该设备置为离线
 *   另一头（后端 config/MqttConfig.java）订的是通配符主题，所以多台设备共用一套代码：
 *     farm/device/+/telemetry  与  farm/device/+/lwt
 *
 * 【对时】NTP（ntp.aliyun.com / ntp1.aliyun.com / pool.ntp.org），时区东八区，
 *         用来给 JSON 里的 "t" 字段填真实时间；板子断电后没有时间，必须联网重新对时。
 */
#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>

// ==================== 需要配置（改这里） ====================
// ---- 平台参数 ----
// DEVICE_NO 是设备在平台里的身份证：它既拼进 MQTT 主题，又对应 device 表的 device_no 列，
// 两边必须一字不差 —— 编号对不上时后端收到消息也查不到设备，会直接丢掉。
const char* DEVICE_NO = "SN-101";        // 平台 device 表的编号（环境监控页新建/查看）  // 平台 device 表的编号，同时决定 MQTT 主题里的 {no}
const char* MQTT_HOST = "192.168.137.1";  // 电脑「移动热点」的固定网关（2026-10-18 改）：Windows 热点网段恒为 192.168.137.x，不再随 DHCP 变化
const int   MQTT_PORT = 1883;            // 内嵌 Moquette broker 端口（随后端启动）  // MQTT 标准端口，与后端 farm.mqtt.port 配置一致

// ---- WiFi 组 1：电脑「移动热点」FARM-AP（2026-10-18 改，现场演示用；必须 2.4G 频段）----
// 板子只连电脑，不碰校园网：校园网要网页认证，ESP32 本来就上不去；电脑自己发热点就没有这个问题。
// 热点设置：名称 FARM-AP / 密码 farm12345 / 频段 2.4GHz（设置→网络和 Internet→移动热点→编辑）
const char* WIFI_SSID_1 = "FARM-AP";              // 电脑移动热点名
const char* WIFI_PASS_1 = "farm12345";            // 电脑移动热点密码

// ---- WiFi 组 2：备用（手机热点，必须 2.4G 频段；组 1 失败时自动连，不用不影响）----
const char* WIFI_SSID_2 = "Redmi K70 Ultra";      // 手机热点名（2026-09-01）
const char* WIFI_PASS_2 = "Hh20040604";           // 手机热点密码（2026-09-01）
// ==========================================================

// ---- 传感器引脚（板面丝印） ----
// 三个引脚号一律与板面丝印一致（D 后面的数字 = GPIO 编号），换线只改这三行。
#define DHT_PIN   4    // DHT11 DATA  // GPIO4 数字脚：读 DHT11 的单总线数据，时序由 DHT 库负责，不能当普通 digitalRead 用
#define SOIL_PIN  32   // 土壤 AOUT  // GPIO32 模拟脚 ADC1_CH4：读土壤探头的 0~4095 原始值
#define LUX_PIN   33   // 光敏 AO  // GPIO33 模拟脚 ADC1_CH5：读光敏模块的 0~4095 原始值

#define DHT_TYPE  DHT11  // 型号常量：把 DHT11 这个标识传给 DHT 库的构造函数，库据此选择时序与精度

// ---- 土壤标定（2026-08-15 实测） ----
// 探头本身不会输出「百分比」，它只输出一个随含水量变化的电阻分压。
// 所以要在「最干」和「最湿」两头各实测一个 ADC 原始值当基准，中间按线性折算 ——
// 这就是下面两个常量存在的理由，它们同时也是换算函数 soilToPercent() 的两个端点。
const int SOIL_AIR_RAW   = 3050;  // 探头悬空  // 标定端点一：折算 0%（ADC 越大越干，方向别弄反）   // 【探头】
const int SOIL_WATER_RAW = 1050;  // 探头泡水  // 标定端点二：折算 100%

// 上报周期取 5 秒的理由：① 演示时曲线要「实时动」，原来 15 秒太慢；
// ② DHT11 两次采样之间本身要留间隔（压到 2 秒以内会读到重复值或直接读失败）；
// ③ 5 秒一条对后端告警判定已经足够及时，sensor_data 表的数据量也可控。
const unsigned long SEND_INTERVAL = 5UL * 1000;   // 上报周期 5s（2026-08-22 从 15s 缩短：实时上报；DHT 采样下限 ≥2s，勿再低于 3s）

// dht：DHT 库对象，绑定引脚和型号，setup() 里 dht.begin() 之后才能读数。
// espClient：ESP32 的 TCP 客户端；PubSubClient 必须架在它上面收发 MQTT 报文。
// mqtt：MQTT 客户端库对象，setup() 里用 setServer() 指向后端 Broker；
//       它的 keepalive 也设成了 5 秒（见 setup），这是「拔电后秒级显示离线」能成立的前提。
DHT dht(DHT_PIN, DHT_TYPE);
WiFiClient espClient;
PubSubClient mqtt(espClient);
// keepalive 5s：LWT 遗嘱触发的关键——broker 靠 keepalive 超时判定断连（拔电无正常断开包），
// 默认 15s 意味着拔电后最迟 15s 才发遗嘱；缩到 5s 后拔电 5s 内必触发秒级离线

unsigned long lastSend = 0;  // 上次上报的时刻（millis() 毫秒计数），loop() 靠它判断够不够 5 秒
unsigned long lastPublishOk = 0;  // 上次「成功发布」的时刻：喂软看门狗，90 秒没发出去就重启（防卡死只能拔插）

// ==================== WiFi 连接（组 1 优先，超时切组 2） ====================
// 尝试连上一组 WiFi（ssid + 密码），最多等 20 秒：连上返回 true，超时返回 false。
// 由 connectWiFi() 调用 —— 组 1 失败后会拿组 2 再调它一次，所以同一个函数被复用两遍。
// 关键逻辑：用 millis() 计时轮询 WiFi.status()，而不是 delay(20000) 死等，
// 这样等待期间串口能实时打印状态，也不会把整块板子卡住。
bool tryWifi(const char* ssid, const char* pass) {
  Serial.printf("WiFi try: %s\n", ssid);
  WiFi.begin(ssid, pass);
  unsigned long start = millis();
  while (millis() - start < 20000) {   // 每组最多等 20 秒
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("WiFi OK: %s (%s)\n", ssid, WiFi.localIP().toString().c_str());
      return true;
    }
    delay(500);
  }
  return false;
}

// 连 WiFi，开机时和每次断线都由它负责。
// 先设成 STA 模式（去连路由器，不做热点）并清掉旧连接，然后「组 1 优先、失败切组 2」，
// 某一组的 SSID 留空字符串就自动跳过该组。
// 两组都连不上也不在这里死循环，只打印一行日志 —— 重试交给 loop() 每圈再做。
// 配两组的原因：宿舍 WiFi 和手机热点互为备份，换演示场地时不用重新烧录。
void connectWiFi() {
  WiFi.mode(WIFI_STA);
  // 关掉 WiFi 省电休眠（2026-09-30 排查所得）：默认的 modem sleep 会让板子周期性睡着、只在 DTIM beacon
  // 时刻醒来收包 —— 实测导致 PC 侧 ping 它丢包 25%、延迟 100~300ms（双峰），进而让 broker 在 8 秒内收不到
  // 心跳而掐断连接、触发 LWT，表现为「设备离线→上线」反复跳。本设备是常电供电，不需要省这点电。
  WiFi.setSleep(false);
  WiFi.disconnect();
  delay(200);
  bool ok = strlen(WIFI_SSID_1) > 0 && tryWifi(WIFI_SSID_1, WIFI_PASS_1);  // 组1留空=跳过，直接连组2
  if (!ok && strlen(WIFI_SSID_2) > 0) {
    ok = tryWifi(WIFI_SSID_2, WIFI_PASS_2);
  }
  if (!ok) {
    Serial.println("WiFi FAIL - will retry in loop()");
  }
}

// ==================== MQTT ====================
// 确保 MQTT 处于已连上状态：没连上就在 while 里反复重连，连上才返回（setup 和 loop 都会调）。
// 没网时直接 return 交回上层，等 WiFi 重连好再进来，避免在这里空转。
// 连接时一次性声明遗嘱 LWT：主题 farm/device/<DEVICE_NO>/lwt、内容 "offline"、QoS 0、不保留，
//   设备拔电或断网时由 Broker 代它发出这条消息，后端收到就能立刻把该设备置为离线。
// clientId = DEVICE_NO + 芯片 MAC 后缀（Broker 允许匿名，不需要账号密码）；连不上等 3 秒再试一次。
void reconnectMQTT() {
  int tries = 0;                              // 连续失败次数：防止在这里死循环卡住整块板子
  while (!mqtt.connected()) {
    if (WiFi.status() != WL_CONNECTED) return;  // 没网先不连，避免空转
    // clientId 用「设备编号 + 芯片 MAC 后 6 位」，broker 无认证；连接时声明 LWT 遗嘱：
    // 拔电/断网导致 TCP 异常断开时，broker 秒级向 farm/device/{no}/lwt 发布 "offline"，
    // 后端订阅后立即置设备离线（无需等上报超时判定）
    char willTopic[40];
    snprintf(willTopic, sizeof(willTopic), "farm/device/%s/lwt", DEVICE_NO);
    // clientId 加芯片 MAC 后缀（2026-11-14）：固定 clientId 时，旧连接若未被 broker 及时回收，
    // 新连接会把旧连接踢掉，而「被踢」会触发 LWT → 后端刚标上线又被标离线，状态反复跳。
    char clientId[48];
    snprintf(clientId, sizeof(clientId), "%s-%06X", DEVICE_NO,
             (uint32_t)(ESP.getEfuseMac() & 0xFFFFFF));
    if (mqtt.connect(clientId, willTopic, 0, false, "offline")) {
      Serial.println("MQTT connected");
    } else {
      Serial.printf("MQTT fail rc=%d, retry in 3s\n", mqtt.state());
      espClient.stop();                       // 关键：丢弃坏掉的 TCP 连接，否则后续 connect 会一直返回 -2
      delay(3000);
      if (++tries >= 20) {                    // 约 1 分钟仍连不上：整体重启，比卡死在这里强
        Serial.println("MQTT give up after 20 tries, restarting");
        ESP.restart();
      }
    }
  }
}

// ==================== 时间（NTP，中国时区 GMT+8） ====================
// 用 NTP 给板子对时，时区写死东八区（GMT+8，偏移量 8*3600 秒）。
// 由 setup() 和「WiFi 重连成功后」两个时机调用：板子自己不带时钟，必须联网才能取到真实时间。
// 备用服务器给了三个（阿里云两个 + 国际 pool.ntp.org），一个不通会自动换下一个。
void syncTime() {
  configTime(8 * 3600, 0, "ntp.aliyun.com", "ntp1.aliyun.com", "pool.ntp.org");
}

// 把当前时间格式化成 "YYYY-MM-DD HH:MM:SS" 字符串，给遥测 JSON 的 "t" 字段用。
// getLocalTime() 最多等 1 秒；对不上时（比如现场只有内网、NTP 出不去）返回空串：
// 上层看到空串就不往 JSON 里放 "t"，后端按「收到报文的时间」落库（IotTelemetryService 有这条兜底），
// 这样没有外网也不会出现 1970-01-01 这种时间戳。
String fmtNow() {
  struct tm t;
  if (!getLocalTime(&t, 1000)) return "";
  char buf[24];
  snprintf(buf, sizeof(buf), "%04d-%02d-%02d %02d:%02d:%02d",
           t.tm_year + 1900, t.tm_mon + 1, t.tm_mday, t.tm_hour, t.tm_min, t.tm_sec);
  return String(buf);
}

// ==================== 传感器 ====================
// 土壤 ADC 原始值 → 湿度百分比（0~100），loop() 每轮都会调它。
// 算法是两头标定 + 线性折算：读到 SOIL_AIR_RAW(3050) 算 0%，读到 SOIL_WATER_RAW(1050) 算 100%。
// 方向别搞反：土越湿 → 探头电阻越小 → AO 电压越低 → ADC 越小，所以是「减完再除以量程」。
// 超出标定范围的值直接夹到 0 / 100，避免算出负数或大于 100 的怪数值。
float soilToPercent(int raw) {
  if (raw >= SOIL_AIR_RAW)   return 0.0f;
  if (raw <= SOIL_WATER_RAW) return 100.0f;
  return (float)(SOIL_AIR_RAW - raw) / (SOIL_AIR_RAW - SOIL_WATER_RAW) * 100.0f;
}

// 开机只跑一次：开串口(115200) → 初始化 DHT11 → 连 WiFi → NTP 对时 → 指向 Broker 并连上 MQTT。
// 先打印两行启动日志，现场一眼就能看出烧的是哪个设备、连的是哪台 Broker。
// 最关键的一行是 mqtt.setKeepAlive(5)：把心跳从库默认的 15 秒压到 5 秒。
//   拔电属于「静默断开」，设备什么都来不及发，Broker 只能靠心跳超时判断它已经不在了；
//   心跳 5 秒意味着最多 5 秒就触发遗嘱把设备置为离线（默认 15 秒要等三倍时间）。
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== Farm Platform ESP32 v1.0 ===");
  Serial.printf("Device=%s Broker=%s:%d\n", DEVICE_NO, MQTT_HOST, MQTT_PORT);
  dht.begin();
  connectWiFi();
  syncTime();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);  // 指向后端电脑上的 Broker（内嵌 Moquette，随后端一起启动）
  // 2026-11-14 调优：keepalive 从 5s 提到 20s。
  // 原因：上报间隔也是 5s，keepalive=5s 时 Moquette 的判定窗口只有 1.5×5=7.5s，
  // DHT 读取 + publish 阻塞一旦超过 2.5s 就被判超时断开，表现为「离线→上线」反复跳。
  // 20s 给 4 倍余量（上报本身就在喂心跳）；拔电后最迟 20s 由 LWT 触发离线。
  mqtt.setKeepAlive(20);
  // socket 读写超时压到 1s（库默认 15s）：坏连接不会把 loop 卡住，也不会让 publish 长时间阻塞
  mqtt.setSocketTimeout(1);
  reconnectMQTT();
}

// 主循环，setup() 之后一直重复执行，每圈干四件事：
//   ① 先看 WiFi 掉没掉，掉了就重连，重连成功补一次对时；
//   ② 再看 MQTT 掉没掉，掉了就重连；
//   ③ 调 mqtt.loop() 让库处理心跳与收包 —— 即使不发数据也必须调，否则会被 Broker 判为断连；
//   ④ 距上次上报满 SEND_INTERVAL(5 秒) 就采一轮数据、拼 JSON、发布出去。
void loop() {
  if (WiFi.status() != WL_CONNECTED) {  // 网掉了就整组重连（WiFi 一断，MQTT 必然也断）
    connectWiFi();   // 断线自动重连（组1→组2）
    if (WiFi.status() == WL_CONNECTED) syncTime();
  }
  if (WiFi.status() == WL_CONNECTED && !mqtt.connected()) {  // 两个条件同时成立才重连 MQTT：网是通的，且 MQTT 确实掉了
    reconnectMQTT();
  }
  // 软看门狗：连续 90 秒没有一次成功发布（≈18 个周期）→ 认为链路已死，直接重启，避免"要人拔插才恢复"
  if (lastPublishOk && millis() - lastPublishOk > 90000UL) {
    Serial.println("No successful publish for 90s, restarting");
    espClient.stop();
    ESP.restart();
  }
  mqtt.loop();  // 让 MQTT 库处理心跳与收包：不发数据时也必须调，否则会被 Broker 判为断连

  // ---- 每 5s 采集并上报 ----
  // 用「时间差」判断该不该上报，而不是 delay(5000) 傻等 —— delay 会把整块板子卡住，
  // 期间收不到也发不出 MQTT 心跳，连接会被 Broker 判死。
  if (millis() - lastSend >= SEND_INTERVAL) {  // 时间差够 SEND_INTERVAL 才进这一轮，并立刻记下本次时刻
    lastSend = millis();

    // 四个原始读数：前两个由 DHT11 直接给出物理量，后两个是 0~4095 的 ADC 原始值。
    float t = dht.readTemperature();          // 气温 ℃
    float h = dht.readHumidity();             // 空气湿度 %
    int   soilRaw = analogRead(SOIL_PIN);     // 土壤 ADC  // 土壤 ADC：0~4095，越大越干
    int   luxRaw  = analogRead(LUX_PIN);      // 光敏 ADC  // 光敏 ADC：0~4095，越亮越小

    float soilPct = soilToPercent(soilRaw);
    // 无土壤温度传感器：按模拟器同款公式近似（airTemp*0.88+2）
    float soilTemp = isnan(t) ? 0.0f : t * 0.88f + 2.0f;  // 三元表达式：DHT 读失败时 t 是 NaN，用 0 兜底，避免把 NaN 拼进 JSON
    // 光敏 ADC(0~4095) 粗略换算 lux：光敏电阻模块 AO 与光照成反比
    // （光强→阻值↓→分压↓→ADC↓，2026-08-25 实测暗处 ADC≈3000、手电筒照射 ADC≈700），
    // 先 (4095-ADC) 取反得到与光照同向的值，再 ×5 映射到 0~20475 lux
    int   lux = (4095 - luxRaw) * 5;
    if (isnan(t)) t = 0.0f;  // 收尾兜底：DHT 读失败时读数为 NaN，统一置 0 再参与拼装（下面 h 同理）
    if (isnan(h)) h = 0.0f;

    // 拼出本设备的遥测主题：farm/device/<DEVICE_NO>/telemetry。
    // 后端订的是通配符 farm/device/+/telemetry，所以换设备编号不用改后端代码。
    String topic = String("farm/device/") + DEVICE_NO + "/telemetry";
    // 拼一行 JSON，字段名与后端白名单一一对应（少字段或多字段都会被后端拒收）：
    //   soil_humidity 土壤湿度% / soil_temp 土温℃ / air_temp 气温℃ /
    //   air_humidity 空气湿度% / light 光照 lux / rssi WiFi 信号强度 dBm
    // 时间字段按需拼：对上时是 "t":"2026-10-18 14:30:00",，没对上就是空串（后端用服务器时间补）
    String timeField = fmtNow();
    timeField = timeField.length() > 0 ? ("\"t\":\"" + timeField + "\",") : "";
    char payload[256];
    snprintf(payload, sizeof(payload),   // 【5秒上报】
             "{%s\"soil_humidity\":%.1f,\"soil_temp\":%.1f,"
             "\"air_temp\":%.1f,\"air_humidity\":%.1f,\"light\":%d,\"rssi\":%d}",  // 最后两个按整数(%d)打印：light=光照 lux，rssi=信号强度 dBm
             timeField.c_str(), soilPct, soilTemp, t, h, lux, WiFi.RSSI());  // 实参顺序与上面的格式串严格一一对应（时间片段/土湿/土温/气温/气湿/光照/信号强度）
    Serial.printf("[%lu] %s\n", millis() / 1000, payload);
    if (mqtt.publish(topic.c_str(), payload)) {   // QoS 0（库默认）：发出去即算完成，不等 Broker 确认、失败也不重发   // 【5秒上报】
      lastPublishOk = millis();                   // 发布成功才喂看门狗，失败则不更新，90 秒后会触发重启
    }
  }
}
