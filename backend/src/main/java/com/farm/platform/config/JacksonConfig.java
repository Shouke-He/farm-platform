package com.farm.platform.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/** 时间格式：LocalDateTime 用 yyyy-MM-dd HH:mm:ss、LocalDate 用 yyyy-MM-dd（yml 的 date-format 管不到它们） （【追问·时区】） */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 出入两个方向必须成对配：只配出参的话，前端传上来的时间字符串会解析失败报 400 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATETIME), new LocalDateSerializer(DATE))
                .deserializers(new LocalDateTimeDeserializer(DATETIME), new LocalDateDeserializer(DATE));
    }
}
