package ru.tyomakr.akcp.ingestion.web.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;

@Component
public class WebImageParser {
  public WebIngestionService.WebParseResult parse(String baseUrl, String html) {
    Document doc = Jsoup.parse(html, baseUrl);
    String title = doc.title();
    Elements images = doc.select("img");
    Set<String> urls = new LinkedHashSet<>();
    for (Element img : images) {
      addIfPresent(urls, img.absUrl("src"));
      addIfPresent(urls, img.absUrl("data-src"));
      addIfPresent(urls, img.absUrl("data-lazy-src"));
    }
    List<ParsedAttachment> attachments = urls.stream()
        .map(ParsedAttachment::new)
        .toList();
    return new WebIngestionService.WebParseResult(baseUrl, title, attachments, null);
  }

  private void addIfPresent(Set<String> urls, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }
    if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
      urls.add(candidate);
    }
  }
}
