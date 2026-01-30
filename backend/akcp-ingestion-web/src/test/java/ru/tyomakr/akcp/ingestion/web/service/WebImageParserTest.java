package ru.tyomakr.akcp.ingestion.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class WebImageParserTest {
  private final WebImageParser parser = new WebImageParser();

  @Test
  void parsesImagesAndResolvesUrls() {
    String html = """
        <html>
          <head><title>Example</title></head>
          <body>
            <img src=\"/img/a.png\" />
            <img data-src=\"https://cdn.example.com/b.jpg\" />
            <img src=\"/img/a.png\" />
          </body>
        </html>
        """;

    WebIngestionService.WebParseResult result = parser.parse("https://example.com/page", html);

    assertThat(result.title()).isEqualTo("Example");
    assertThat(result.attachments()).hasSize(2);
    assertThat(result.attachments().get(0).url()).isEqualTo("https://example.com/img/a.png");
  }
}
