package dev.kalli.demo.tracing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class JokeController {

    private final JokeService jokeService;

    @GetMapping("/joke")
    public String getJoke(@RequestParam(name = "topic") String topic) {
        return jokeService.getJoke(topic);
    }
}
