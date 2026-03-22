package com.alibaba.cloud.ai.studio.admin.config;

import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.MediaHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.content.Media;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    /**
     * 配置ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

                // Configure deserialization features
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        // Register Media serializer/deserializer to handle byte[] and other data types
        SimpleModule mediaModule = new SimpleModule();
        mediaModule.addSerializer(Media.class, new MediaHandler.Serializer());
        mediaModule.addDeserializer(Media.class, new MediaHandler.Deserializer());
        objectMapper.registerModule(mediaModule);

        return objectMapper;
    }
}
