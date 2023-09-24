package dev.kalli.demo.tracing;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class JokeService {

    private final AiClient aiClient;

    @Observed(name = "joke.topic",
            contextualName = "getting-joke-by-topic"
    )
    public String getJoke(String topic) {
        PromptTemplate promptTemplate = new PromptTemplate("""
                I'm bored with hello world apps. How about you give me a joke about {topic}? to get started?
                Include some programming terms in your joke to make it more fun. Do not make any comments.
                """);
        promptTemplate.add("topic", topic);

        log.info("Generating joke for topic {}", topic);
        Prompt prompt = promptTemplate.create();
        return aiClient.generate(prompt)
                .getGeneration()
                .getText();
    }
}
