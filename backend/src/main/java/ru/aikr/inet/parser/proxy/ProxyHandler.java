package ru.aikr.inet.parser.proxy;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.util.AnsiColors;

import java.io.IOException;
import java.util.logging.Logger;

@Component
public class ProxyHandler {

    private static final Logger log = Logger.getLogger(ProxyHandler.class.getName());

    @Value("${env.parser.proxy.proxy-url}")
    private String proxyUrl;

    @Value("${env.parser.proxy.proxy-port}")
    private String proxyPort;


    public void retryWithProxy(String url) {
        try {
            log.info(AnsiColors.CYAN + "Retrying with proxy..." + AnsiColors.RESET);
            Connection connection = Jsoup.connect(url)
                    .proxy(proxyUrl, Integer.parseInt(proxyPort))
                    .timeout(30000);
            connection.execute();
        } catch (IOException e) {
            log.severe(AnsiColors.RED + String.format(
                    "Proxy retry failed: %s | URL: %s", e.getMessage(), url
            ) + AnsiColors.RESET);
        }
    }
}
