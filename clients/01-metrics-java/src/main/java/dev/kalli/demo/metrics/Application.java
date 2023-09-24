package dev.kalli.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    private final Timer.Builder timer = Timer.builder("http_server_request_seconds")
            .description("Duration of HTTP requests in seconds")
            .sla(Duration.ofMillis(5),
                    Duration.ofMillis(10),
                    Duration.ofMillis(25),
                    Duration.ofMillis(50),
                    Duration.ofMillis(100),
                    Duration.ofMillis(250),
                    Duration.ofMillis(500),
                    Duration.ofMillis(1000),
                    Duration.ofMillis(2500),
                    Duration.ofMillis(5000),
                    Duration.ofMillis(10000));

    private final Counter.Builder counter = Counter.builder("http_server_requests_total")
            .description("Number of HTTP requests");

    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public String ping() throws InterruptedException {
        Instant start = Instant.now();
        Double sleepDuration = Math.random() * 1000;
        log.info("Sleeping for {} ms", sleepDuration);

        Thread.sleep(sleepDuration.longValue());

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        List<Tag> tags = List.of(
                Tag.of("status", "200"),
                Tag.of("method", "GET"),
                Tag.of("uri", "/ping")
        );

        timer.tags(tags)
                .register(Metrics.globalRegistry)
                .record(duration);

        counter.tags(tags)
                .register(Metrics.globalRegistry)
                .increment();

        return "{\"message\": \"pong\"}";
    }
}
