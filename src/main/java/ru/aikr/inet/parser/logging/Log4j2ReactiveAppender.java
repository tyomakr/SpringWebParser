package ru.aikr.inet.parser.logging;

import lombok.Setter;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

@Plugin(name="ReactiveAppender", category="Core", elementType="appender", printObject=true)
public class Log4j2ReactiveAppender extends AbstractAppender {

    /**
     * -- SETTER --
     * Спринг вызовет этот метод при старте, чтобы подцепить паблишер
     */
    @Setter
    private static volatile LogEventsPublisher publisher;
    /**
     * -- SETTER --
     * Спринг вызовет этот метод при старте, чтобы подцепить паблишер
     */

    protected Log4j2ReactiveAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    @PluginFactory
    public static Log4j2ReactiveAppender createAppender(@PluginAttribute("name") String name) {
        return new Log4j2ReactiveAppender(
                name,
                null,
                PatternLayout.newBuilder().withPattern("%d{HH:mm:ss} [%t] %-5level %logger{36} - %msg%n").build()
        );
    }

    @Override
    public void append(LogEvent event) {
        if (publisher != null) {
            String message = new String(getLayout().toByteArray(event));
            publisher.publish(message);
        }
    }
}
