package com.dream.inventory.config;

import com.dream.inventory.common.TimeZones;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonTimeZoneCustomizer() {
        return builder -> builder.defaultTimeZone(TimeZone.getTimeZone(TimeZones.ID));
    }
}
