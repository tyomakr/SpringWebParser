package ru.tyomakr.akcp.library.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.AnalysisProfileVersion;
import ru.tyomakr.akcp.core.content.AnalysisTextEvidence;
import ru.tyomakr.akcp.core.content.AnalysisTextRole;
import ru.tyomakr.akcp.core.content.MediaAnalysisInput;
import ru.tyomakr.akcp.core.content.MediaAnalysisResult;
import ru.tyomakr.akcp.core.content.StorageReference;

class DeterministicPixelAnalysisProviderTest {
  @Test
  void fixtureProviderIsStableAndKeepsTextRoleSeparateFromArea() throws Exception {
    byte[] bytes = png();
    String sha = sha256(bytes);
    AnalysisTextEvidence text = new AnalysisTextEvidence(
        0.80d,
        AnalysisTextRole.SUPPORTING,
        0.8d,
        "fixture-text-v1",
        "fixture");
    DeterministicPixelAnalysisProvider provider = new DeterministicPixelAnalysisProvider(
        Map.of(sha, text),
        1000L);
    MediaAnalysisInput input = new MediaAnalysisInput(
        UUID.randomUUID(),
        sha,
        StorageReference.fromSha256(sha),
        new AnalysisProfileVersion("fixture-v1"));

    MediaAnalysisResult first = provider.analyze(input, bytes);
    MediaAnalysisResult second = provider.analyze(input, bytes);

    assertThat(first).isEqualTo(second);
    assertThat(first.visualVector()).hasSize(48);
    assertThat(first.textEvidence().isDominant(0.65d)).isFalse();
    assertThat(first.explanations()).extracting("code")
        .containsExactly("provider", "text-evidence");
  }

  @Test
  void invalidImageAndPixelLimitFailClosed() throws Exception {
    DeterministicPixelAnalysisProvider provider = new DeterministicPixelAnalysisProvider(Map.of(), 1L);
    byte[] bytes = png();
    String sha = sha256(bytes);
    MediaAnalysisInput input = new MediaAnalysisInput(
        UUID.randomUUID(),
        sha,
        StorageReference.fromSha256(sha),
        new AnalysisProfileVersion("fixture-v1"));

    assertThatThrownBy(() -> provider.analyze(input, "not-image".getBytes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA");
    assertThatThrownBy(() -> provider.analyze(input, bytes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pixel limit");
  }

  private byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 4; y++) {
      for (int x = 0; x < 4; x++) {
        image.setRGB(x, y, (x + y) % 2 == 0 ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
      }
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, "png", output)).isTrue();
    return output.toByteArray();
  }

  private String sha256(byte[] bytes) throws Exception {
    return java.util.HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
