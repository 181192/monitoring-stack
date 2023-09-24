package dev.kalli.demo.tracing;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/weather")
    public Mono<WeatherService.Weather> get(Double latitude, Double longitude) {
        return weatherService.get(latitude, longitude);
    }
}
