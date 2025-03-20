package ru.aikr.inet.parser.network;

import org.jsoup.Connection;
import java.io.IOException;

public interface ConnectionConfigurator {
    Connection.Response configureConnection(String url) throws IOException;
    String getLastUserAgent();


}