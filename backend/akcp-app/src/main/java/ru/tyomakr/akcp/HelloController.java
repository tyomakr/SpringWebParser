package ru.tyomakr.akcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {
  @GetMapping("/api/hello")
  public Mono<String> hello() {
    return Mono.just("AKCP is running");
  }
}
