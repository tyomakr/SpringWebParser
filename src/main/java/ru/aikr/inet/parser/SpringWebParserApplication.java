package ru.aikr.inet.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class SpringWebParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringWebParserApplication.class, args);
    }

}
