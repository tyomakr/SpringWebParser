package ru.tyomakr.akcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "ru.tyomakr.akcp")
@EnableScheduling
public class AkcpApplication {
  public static void main(String[] args) {
    SpringApplication.run(AkcpApplication.class, args);
  }
}
