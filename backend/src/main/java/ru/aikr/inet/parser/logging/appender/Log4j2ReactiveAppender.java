package ru.aikr.inet.parser.logging.appender;

import lombok.Setter;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.io.Serializable;
import java.util.Arrays;

@Plugin(name="ReactiveAppender", category=Core.CATEGORY_NAME,
        elementType=Appender.ELEMENT_TYPE, printObject=true)
public class Log4j2ReactiveAppender extends AbstractAppender {

    @Setter private static volatile LogEventsPublisher publisher;

    /* ───── фабрика ───── */
    @PluginFactory
    public static Log4j2ReactiveAppender create(
            @PluginAttribute("name") String name,
            @PluginAttribute(value="ignoreExceptions", defaultBoolean=true) boolean ignore) {

        // перевод строки оставляем!
        String pattern = "%d{HH:mm:ss} %-5level %msg%n";
        return new Log4j2ReactiveAppender(
                name, null,
                PatternLayout.newBuilder().withPattern(pattern).build(),
                ignore, Property.EMPTY_ARRAY);
    }

    private Log4j2ReactiveAppender(String n, Filter f,
                                   Layout<? extends Serializable> l,
                                   boolean ie, Property[] p) {
        super(n, f, l, ie, p);
    }

    /* ───── главное ───── */
    @Override
    public void append(LogEvent event) {
        if (publisher == null) return;

        // строка + \n (CR убираем на Windows)
        String raw = new String(getLayout().toByteArray(event)).replace("\r", "");

        // делим по любым переводам строки
        Arrays.stream(raw.split("\\R"))
                .filter(s -> !s.isBlank())
                .forEach(publisher::publish);
    }
}
