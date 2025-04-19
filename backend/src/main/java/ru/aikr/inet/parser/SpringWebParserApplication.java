package ru.aikr.inet.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import ru.aikr.inet.parser.logging.Log4j2ReactiveAppender;
import ru.aikr.inet.parser.logging.LogEventsPublisher;

@SpringBootApplication
public class SpringWebParserApplication implements ApplicationContextAware {

    public static void main(String[] args) {
        SpringApplication.run(SpringWebParserApplication.class, args);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        // как только контекст поднят – даём аппендеру ссылку на наш паблишер
        LogEventsPublisher publisher = ctx.getBean(LogEventsPublisher.class);
        Log4j2ReactiveAppender.setPublisher(publisher);
    }



}