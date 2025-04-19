package ru.aikr.inet.parser.logging;

import lombok.Setter;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

/** Appender, пересылающий строки лога в LogEventsPublisher. */
@Plugin(
        name      = "ReactiveAppender",
        category  = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE,
        printObject = true
)
public class Log4j2ReactiveAppender extends AbstractAppender {

    /** Инжектируется Spring‑контекстом */
    @Setter
    private static volatile LogEventsPublisher publisher;

    /* ---- новый (не‑deprecated) конструктор ---- */
    protected Log4j2ReactiveAppender(
            String name,
            Filter filter,
            Layout<? extends Serializable> layout,
            boolean ignoreExceptions,
            Property[] properties
    ) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    /* ---- фабрика для Log4j2 ---- */
    @SuppressWarnings("unused")  // вызывается Log4j2 через reflection
    @PluginFactory
    public static Log4j2ReactiveAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignore
    ) {
        return new Log4j2ReactiveAppender(
                name,
                null,
                PatternLayout.newBuilder()
                        .withPattern("%d{HH:mm:ss} [%-15t] %-5level %logger{36} - %msg%n")
                        .build(),
                ignore,
                Property.EMPTY_ARRAY           // 5‑й параметр
        );
    }

    @Override
    public void append(LogEvent event) {
        if (publisher != null) {
            String line = new String(getLayout().toByteArray(event));
            publisher.publish(line);
        }
    }
}