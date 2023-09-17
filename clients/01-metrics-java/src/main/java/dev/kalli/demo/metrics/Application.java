package dev.kalli.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routes() {

        Timer.Builder timer = Timer.builder("http_request_duration_seconds")
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

        Counter.Builder counter = Counter.builder("http_requests_total")
                .description("Number of HTTP requests");

        return RouterFunctions.route()
                .GET("/ping", req -> {

                    Instant start = Instant.now();
                    Double sleepDuration = Math.random() * 1000;
                    log.info("Sleeping for {} ms", sleepDuration);

                    try {
                        Thread.sleep(sleepDuration.longValue());
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

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

                    return ServerResponse.ok().bodyValue("pong");

                })
                .build();
    }
}
