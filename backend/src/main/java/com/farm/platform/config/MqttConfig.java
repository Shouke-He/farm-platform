package com.farm.platform.config;

import com.farm.platform.service.IotTelemetryService;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** MQTT 接入：应用就绪后内嵌启动 Moquette Broker（标准 MQTT 协议、零外部依赖），并用 Paho 客户端订自己的遥测与 LWT */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttConfig {

    @Value("${farm.mqtt.port:1883}")   // Broker 端口
    private int port;

    private final IotTelemetryService telemetryService;

    private Server broker;
    private MqttClient client;

    @EventListener(ApplicationReadyEvent.class)   // 启动完成后再连 Broker
    public void start() {
        try {
            Properties props = new Properties();
            props.put("port", String.valueOf(port));
            // 监听本机所有网卡：只有绑 0.0.0.0，局域网里的 ESP32 才连得进来（绑 127.0.0.1 就只有本机能连）
            props.put("host", "0.0.0.0");   // 【内嵌Broker】
            // 允许匿名连接：设备端固件不带账号密码直连，局域网演示环境够用
            props.put("allow_anonymous", "true");   // 【鉴权】
            // 内存模式：不配置 data_path/persistent_store，避免 Moquette 0.17 持久化空路径崩溃
            props.put("persistence_enabled", "false");
            // 关掉 Moquette 自带的匿名遥测（开启时会在启动目录写 .moquette_uuid 文件）
            props.put("telemetry_enabled", "false");
            broker = new Server();
            broker.startServer(new MemoryConfig(props));
            log.info("MQTT 内嵌 Broker 启动成功: tcp://0.0.0.0:{}（局域网设备可接入）", port);

            client = new MqttClient("tcp://127.0.0.1:" + port, "farm-server");
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            client.connect(opts);
            client.subscribe("farm/device/+/telemetry", 0, (topic, msg) -> {
                String[] parts = topic.split("/");
                if (parts.length == 4) {
                    telemetryService.handle(parts[2], new String(msg.getPayload(), StandardCharsets.UTF_8));
                }
            });
            // LWT 遗嘱：设备异常断连（拔电/断网）时 broker 即时发布，秒级置离线
            client.subscribe("farm/device/+/lwt", 0, (topic, msg) -> {
                String[] parts = topic.split("/");
                if (parts.length == 4) {
                    telemetryService.markOffline(parts[2]);
                }
            });
            log.info("Paho 客户端已订阅 farm/device/+/telemetry 与 +/lwt");
        } catch (Exception e) {
            // 起不来只打日志：8080 的接口、数据库、WebSocket、定时任务都不受影响
            log.error("MQTT 启动失败（不影响其他功能）: {}", e.getMessage(), e);
        }
    }

    public void publish(String topic, String payload) {
        try {
            if (client != null && client.isConnected()) {
                client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0, false);
            }
        } catch (MqttException e) {
            log.warn("MQTT 发布失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null) {
                client.disconnect();
                client.close();
            }
        } catch (Exception ignored) {
        }
        if (broker != null) {
            broker.stopServer();
        }
    }
}
