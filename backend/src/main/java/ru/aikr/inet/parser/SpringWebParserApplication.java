package ru.aikr.inet.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import ru.aikr.inet.parser.logging.Log4j2ReactiveAppender;
import ru.aikr.inet.parser.logging.LogEventsPublisher;

@SpringBootApplication
public class SpringWebParserApplication implements ApplicationContextAware {

    public static void main(String[] args) {
        SpringApplication.run(SpringWebParserApplication.class, args);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) {
        ObjectProvider<LogEventsPublisher> provider = ctx.getBeanProvider(LogEventsPublisher.class);
        LogEventsPublisher publisher = provider.getIfAvailable();
        if (publisher != null) {
            Log4j2ReactiveAppender.setPublisher(publisher);
        }
    }
}