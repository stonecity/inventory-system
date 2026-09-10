package com.dream.inventory.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ims.jwt")
public class JwtProperties {

    private String secret = "ims-dev-secret-key-change-in-production-min-256-bits-long!!";
    private long expirationMs = 86400000L;
}
