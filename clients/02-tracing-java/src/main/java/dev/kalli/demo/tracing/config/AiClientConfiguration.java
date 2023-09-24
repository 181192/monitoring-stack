package dev.kalli.demo.tracing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.springframework.ai.autoconfigure.openai.OpenAiProperties;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.time.Duration;

@Slf4j
@Component
public class AiClientConfiguration {
    private static final String BASE_URL = "https://api.openai.com/";

    @Bean
    @Primary
    public AiClient aiClient(OpenAiProperties properties,
                             OpenTelemetry openTelemetry) {

        if (properties.getApiKey() == null) {
            log.error("OpenAI API key must be provided");
            throw new IllegalArgumentException("OpenAI API key must be provided");
        }

        Duration timeout = Duration.ofSeconds(10);
        OkHttpClient client = OpenAiService.defaultClient(properties.getApiKey(), timeout);
        Call.Factory tracedClient = createTracedClient(openTelemetry, client);

        ObjectMapper mapper = OpenAiService.defaultObjectMapper();
        Retrofit retrofit = createRetrofitClient(tracedClient, mapper);

        OpenAiService openAiService = new OpenAiService(retrofit.create(OpenAiApi.class));
        return new OpenAiClient(openAiService);
    }

    private Call.Factory createTracedClient(OpenTelemetry openTelemetry, OkHttpClient client) {
        return OkHttpTelemetry.builder(openTelemetry)
                .build()
                .newCallFactory(client);
    }

    private Retrofit createRetrofitClient(Call.Factory tracedClient, ObjectMapper mapper) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .callFactory(tracedClient)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
    }
}
