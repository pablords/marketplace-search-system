package com.marketplace.search.catalog.bootstrap.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "debezium.connector")
@Data
public class DebeziumConnectorProperties {
    private Map<String, String> config;
}