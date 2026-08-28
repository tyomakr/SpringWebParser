package ru.tyomakr.akcp.ingestion.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;

class FishkiImageParserTest {
  @Test
  void parseUsesContainerSelector() {
    String html = "<html><body>"
        + "<div class=\"embed-image-single\"><img src=\"/a.jpg\"></div>"
        + "<div class=\"other\"><img src=\"/b.jpg\"></div>"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1", html, "div.embed-image-single");

    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).url()).isEqualTo("https://fishki.net/a.jpg");
  }

  @Test
  void parseSupportsImageSelector() {
    String html = "<html><body>"
        + "<img class=\"lazy_load_image_past\" src=\"/a.jpg\">"
        + "<img class=\"lazy_load_image_past\" src=\"/b.jpg\">"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1/", html, "img.lazy_load_image_past");

    assertThat(attachments).hasSize(2);
    assertThat(attachments.get(0).url()).isEqualTo("https://fishki.net/a.jpg");
  }

  @Test
  void parseSupportsAnchorHrefInsideContainer() {
    String html = "<html><body>"
        + "<div class=\"embed-image-single\"><a href=\"https://cdn.fishki.net/a.jpg\">x</a></div>"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1/", html, "div.embed-image-single");

    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).url()).isEqualTo("https://cdn.fishki.net/a.jpg");
  }

  @Test
  void parseSupportsVideoLinks() {
    String html = "<html><body>"
        + "<div class=\"embed-image-single\"><video src=\"/clip.mp4\"></video></div>"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1/", html, "div.embed-image-single");

    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).url()).isEqualTo("https://fishki.net/clip.mp4");
  }

  @Test
  void parseSupportsVideoSourceInsideVideoJs() {
    String html = "<html><body>"
        + "<div class=\"comment__text\">"
        + "<video class=\"vjs-tech\"><source src=\"https://cdn.fishki.net/a.mp4\" type=\"video/mp4\"></video>"
        + "</div>"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1/", html, "div.embed-image-single");

    assertThat(attachments).extracting(ParsedAttachment::url)
        .contains("https://cdn.fishki.net/a.mp4");
  }

  @Test
  void parseUsesOnlyCommentMediaWhenCommentsPresent() {
    String html = "<html><body>"
        + "<a class=\"tiny__info__img\" href=\"https://tn.fishki.net/banner.jpg\"></a>"
        + "<div class=\"comment__text\">"
        + "<div class=\"embed-image-single\"><a href=\"https://sos.fishki.net/a.jpg\">x</a></div>"
        + "</div>"
        + "</body></html>";
    FishkiImageParser parser = new FishkiImageParser();

    List<ParsedAttachment> attachments = parser.parse("https://fishki.net/mix/1/", html, "div.embed-image-single");

    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).url()).isEqualTo("https://sos.fishki.net/a.jpg");
  }
}
