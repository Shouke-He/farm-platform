package com.farm.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 和风天气数据服务：拉取 3 天天气预报（JDK HttpClient，零额外依赖） */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    @Value("${farm.weather.key}")   // 和风 API Key
    private String key;

    @Value("${farm.weather.location}")   // 默认城市 ID
    private String location;

    @Value("${farm.weather.host}")   // 专属域名
    private String host;

    private final ObjectMapper objectMapper;

    /** 建连接的超时时间：8 秒。另有"整个请求"的超时 10 秒（见 forecast3d 里的 timeout） */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** 一天的预报摘要（不可变的小数据载体）。 */
    public record DayWeather(String fxDate, String textDay, int tempMax, int tempMin, int windScaleDay) {
    }

    /** 和风城市 ID → 中文名（与 application.yml farm.weather.cities / 注册页下拉同步） */
    private static final Map<String, String> CITY_NAMES = Map.of(
            "101300101", "南宁市",
            "101300301", "柳州市",
            "101300501", "桂林市");

    /** 城市 ID 转中文名，用于预警标题和统计看板；查不到的一律当南宁 */
    public static String cityName(String cityId) {
        return CITY_NAMES.getOrDefault(cityId, "南宁市");
    }

    /** 拉取未来 3 天预报（默认主城市）；失败返回空列表（不抛出，避免定时任务中断） */
    public List<DayWeather> forecast3d() {
        return forecast3d(location);
    }

    /** 拉取指定城市未来 3 天预报（多城市天气引擎：cities 列表逐个扫描）。 */
    public List<DayWeather> forecast3d(String cityId) {
        try {
            // 组装 GET 请求：/v7/weather/3d?location={城市ID}，身份靠请求头 X-QW-Api-Key 携带
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/v7/weather/3d?location=" + cityId))   // 【拉三天预报】
                    .header("Accept", "application/json")
                    .header("X-QW-Api-Key", key)
                    .timeout(Duration.ofSeconds(10))   // 整个请求（连接+传输+读取）最多等 10 秒
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            // 第一层 200：HTTP 状态码，代表网络层请求成功
            if (resp.statusCode() != 200) {
                log.warn("天气接口异常 HTTP {}", resp.statusCode());
                return List.of();
            }
            // 和风始终返回 gzip，需手动解压
            String body = new String(new GZIPInputStream(resp.body()).readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(body);
            // 第二层 200：响应体里的业务状态码。必须两层都判——HTTP 200 但业务码是 401（key 失效）很常见。
            if (root.path("code").asText().equals("200") && root.has("daily")) {
                List<DayWeather> list = new ArrayList<>();
                // daily 是个数组，逐个字段取出来封装成 DayWeather
                for (JsonNode d : root.path("daily")) {
                    list.add(new DayWeather(
                            d.path("fxDate").asText(),       // 预报日期
                            d.path("textDay").asText(),      // 白天天气文字，如"中雨"
                            d.path("tempMax").asInt(),       // 最高温
                            d.path("tempMin").asInt(),       // 最低温
                            parseWind(d.path("windScaleDay").asText())));   // 白天风力等级（"1-3" 这类区间取上界）
                }
                return list;
            }
            log.warn("天气接口返回异常: {}", resp.body());
            return List.of();
        } catch (Exception e) {
            // 网络不通、超时、gzip 解压失败、JSON 格式异常等全部在这里收口 →
            log.warn("天气接口调用失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 风力等级文本 -> 整数。和风实际给的是**区间**写法（如 "1-3"、"3-4"），偶尔才是单值（如 "5"）， */
    private int parseWind(String windScale) {
        if (windScale == null || windScale.isBlank()) {
            return 0;
        }
        int max = 0;
        for (String part : windScale.split("[^0-9]+")) {
            // 长度限制只是防呆：风力等级不可能超过 3 位数，超长的一律当异常数据忽略，避免 parseInt 溢出
            if (!part.isEmpty() && part.length() <= 3) {
                max = Math.max(max, Integer.parseInt(part));
            }
        }
        return max;
    }
}
