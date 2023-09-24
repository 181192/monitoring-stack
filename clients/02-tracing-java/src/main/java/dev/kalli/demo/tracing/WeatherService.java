package dev.kalli.demo.tracing;

import dev.kalli.demo.tracing.config.GeoLocationConfig;
import dev.kalli.demo.tracing.config.WeatherConfig;
import io.micrometer.observation.ObservationRegistry;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class WeatherService {

    private final WebClient webClient;
    private final AiClient aiClient;
    private final ObservationRegistry registry;
    private final GeoLocationConfig geoLocationConfig;
    private final WeatherConfig weatherConfig;

    public Mono<Weather> get(Double latitude, Double longitude) {
        return Mono.zip(getGeoLocation(latitude, longitude), getWeather(latitude, longitude))
                .flatMap(tuple -> {
                    GeoLocationResponse geoLocationResponse = tuple.getT1();
                    WeatherResponse weatherResponse = tuple.getT2();
                    return getMessage(weatherResponse, geoLocationResponse)
                            .map(message -> Weather.builder()
                                    .message(message)
                                    .address(geoLocationResponse.address())
                                    .temperature(weatherResponse.temperature())
                                    .windSpeed(weatherResponse.windSpeed())
                                    .weatherSymbol(weatherResponse.weatherSymbol())
                                    .build());
                })
                .name("getting-weather")
                .tap(Micrometer.observation(registry, observationRegistry ->
                        Objects.requireNonNull(observationRegistry.getCurrentObservation())
                                .highCardinalityKeyValue("latitude", String.valueOf(latitude))
                                .highCardinalityKeyValue("longitude", String.valueOf(longitude))
                ));
    }

    private Mono<GeoLocationResponse> getGeoLocation(Double latitude, Double longitude) {
        return webClient.get()
                .uri(geoLocationConfig.getBaseUrl() + "/reverse-geocode?latitude={latitude}&longitude={longitude}", latitude, longitude)
                .retrieve()
                .bodyToMono(GeoLocationResponse.class)
                .timeout(geoLocationConfig.getTimeout())
                .name("getting-geolocation")
                .tap(Micrometer.observation(registry));
    }

    private Mono<WeatherResponse> getWeather(Double latitude, Double longitude) {
        return webClient.get()
                .uri(weatherConfig.getBaseUrl() + "/weather?latitude={latitude}&longitude={longitude}", latitude, longitude)
                .retrieve()
                .bodyToMono(WeatherResponse.class)
                .timeout(weatherConfig.getTimeout())
                .name("getting-weather-information")
                .tap(Micrometer.observation(registry));
    }

    public Mono<String> getMessage(WeatherResponse weatherResponse, GeoLocationResponse geoLocationResponse) {
        PromptTemplate promptTemplate = new PromptTemplate("""
                I'm bored with hello world apps. How about you give me some nice motivating words for the day
                based on the current temperature is {temperature} degrees, the wind speed is {windSpeed} m/s,
                the weather is {weatherSymbol} and I'm located at {address}?
                Do not make any comments. Reply in norwegian.
                """);

        promptTemplate.add("address", geoLocationResponse.address());
        promptTemplate.add("temperature", weatherResponse.temperature());
        promptTemplate.add("windSpeed", weatherResponse.windSpeed());
        promptTemplate.add("weatherSymbol", weatherResponse.weatherSymbol());

        log.info("Generating weather message");
        Prompt prompt = promptTemplate.create();
        return Mono.fromCallable(() -> aiClient.generate(prompt)
                        .getGeneration()
                        .getText())
                .subscribeOn(Schedulers.boundedElastic())
                .name("getting-weather-message")
                .tap(Micrometer.observation(registry));
    }

    @Builder
    public record Weather(
            String message,
            String address,
            Double temperature,
            Double windSpeed,
            String weatherSymbol) {
    }


    public record GeoLocationResponse(
            String address) {
    }

    public record WeatherResponse(
            Double temperature,
            Double windSpeed,
            String weatherSymbol) {
    }
}
