package dev.kalli.demo.tracing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "clients.weather")
public class WeatherConfig {
    String baseUrl;
    Duration timeout;
}
