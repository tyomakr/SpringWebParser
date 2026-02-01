package ru.tyomakr.akcp.ingestion.web.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;

@Component
public class FishkiImageParser {
  public List<ParsedAttachment> parse(String baseUrl, String html, String containerSelector) {
    Document doc = Jsoup.parse(html, baseUrl);
    Set<String> urls = new LinkedHashSet<>();
    Elements commentBlocks = doc.select("div.comment__text");
    if (!commentBlocks.isEmpty()) {
      for (Element comment : commentBlocks) {
        addFromAnchors(urls, comment.select("div.embed-image-single a[href]"));
        addFromVideos(urls, comment.select("video, video source"));
      }
      return urls.stream().map(ParsedAttachment::new).toList();
    }
    if (shouldPreferEmbed(containerSelector)) {
      List<ParsedAttachment> embedAttachments = parseEmbedAnchors(doc);
      embedAttachments.forEach(attachment -> urls.add(attachment.url()));
    }
    Elements selected = containerSelector == null || containerSelector.isBlank()
        ? doc.select("img")
        : doc.select(containerSelector);
    if (containerSelector == null || containerSelector.isBlank()) {
      addFromImages(urls, doc.select("img"));
      addFromVideos(urls, doc.select("video, video source"));
      addFromAnchors(urls, doc.select("a[href]"));
      return urls.stream().map(ParsedAttachment::new).toList();
    }

    if (selected.isEmpty()) {
      addFromImages(urls, doc.select("img"));
      addFromVideos(urls, doc.select("video, video source"));
      addFromAnchors(urls, doc.select("a[href]"));
      return urls.stream().map(ParsedAttachment::new).toList();
    }

    for (Element element : selected) {
      if ("img".equalsIgnoreCase(element.tagName())) {
        addFromImages(urls, new Elements(element));
      } else if ("video".equalsIgnoreCase(element.tagName())) {
        addFromVideos(urls, new Elements(element));
      } else if ("a".equalsIgnoreCase(element.tagName())) {
        addFromAnchors(urls, new Elements(element));
      } else {
        addFromImages(urls, element.select("img"));
        addFromVideos(urls, element.select("video, video source"));
        addFromAnchors(urls, element.select("a[href]"));
      }
    }
    addFromVideos(urls, doc.select("video, video source"));
    addFromAnchors(urls, doc.select("a[href$=.gif], a[href$=.GIF], a[href$=.mp4], a[href$=.MP4], a[href$=.webm], a[href$=.WEBM], a[href$=.webp], a[href$=.WEBP]"));
    if (urls.isEmpty()) {
      addFromImages(urls, doc.select("img"));
      addFromVideos(urls, doc.select("video, video source"));
      addFromAnchors(urls, doc.select("a[href]"));
    }
    return urls.stream().map(ParsedAttachment::new).toList();
  }

  private void addFromImages(Set<String> urls, Elements images) {
    for (Element img : images) {
      addIfPresent(urls, img.absUrl("src"));
      addIfPresent(urls, img.absUrl("data-src"));
      addIfPresent(urls, img.absUrl("data-lazy-src"));
    }
  }

  private void addFromAnchors(Set<String> urls, Elements links) {
    for (Element link : links) {
      String href = link.absUrl("href");
      if (href == null || href.isBlank()) {
        continue;
      }
      if (isMediaLink(href)) {
        urls.add(href);
      }
    }
  }

  private List<ParsedAttachment> parseEmbedAnchors(Document doc) {
    Set<String> urls = new LinkedHashSet<>();
    addFromAnchors(urls, doc.select("div.embed-image-single a[href]"));
    addFromVideos(urls, doc.select("div.embed-image-single video, div.embed-image-single video source"));
    return urls.stream().map(ParsedAttachment::new).toList();
  }

  private boolean shouldPreferEmbed(String containerSelector) {
    if (containerSelector == null || containerSelector.isBlank()) {
      return true;
    }
    return containerSelector.contains("embed-image-single");
  }

  private boolean isMediaLink(String href) {
    String normalized = href;
    int queryIndex = normalized.indexOf('?');
    if (queryIndex >= 0) {
      normalized = normalized.substring(0, queryIndex);
    }
    int hashIndex = normalized.indexOf('#');
    if (hashIndex >= 0) {
      normalized = normalized.substring(0, hashIndex);
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    return lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".gif")
        || lower.endsWith(".webp")
        || lower.endsWith(".bmp")
        || lower.endsWith(".mp4")
        || lower.endsWith(".webm");
  }

  private void addIfPresent(Set<String> urls, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }
    if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
      urls.add(candidate);
    }
  }

  private void addFromVideos(Set<String> urls, Elements videos) {
    for (Element video : videos) {
      if ("video".equalsIgnoreCase(video.tagName())) {
        addIfPresent(urls, video.absUrl("src"));
        addIfPresent(urls, video.absUrl("data-src"));
        addIfPresent(urls, video.absUrl("data-video"));
        addFromVideos(urls, video.select("source"));
      } else if ("source".equalsIgnoreCase(video.tagName())) {
        addIfPresent(urls, video.absUrl("src"));
        addIfPresent(urls, video.absUrl("data-src"));
      }
    }
  }
}
