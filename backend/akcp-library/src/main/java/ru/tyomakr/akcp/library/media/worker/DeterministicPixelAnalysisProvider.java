package ru.tyomakr.akcp.library.media.worker;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import ru.tyomakr.akcp.core.content.AnalysisArtifactManifest;
import ru.tyomakr.akcp.core.content.AnalysisExplanation;
import ru.tyomakr.akcp.core.content.AnalysisProviderDescriptor;
import ru.tyomakr.akcp.core.content.AnalysisTextEvidence;
import ru.tyomakr.akcp.core.content.MediaAnalysisInput;
import ru.tyomakr.akcp.core.content.MediaAnalysisProvider;
import ru.tyomakr.akcp.core.content.MediaAnalysisResult;

/**
 * Offline fixture provider for worker contract tests and benchmark plumbing.
 *
 * <p>The vector is a pixel descriptor, not a semantic embedding, and text evidence is supplied
 * by the fixture map, not inferred by OCR. Production OCR/model providers remain a separate
 * artifact-review decision.
 */
public final class DeterministicPixelAnalysisProvider implements MediaAnalysisProvider {
  private static final int HASH_GRID = 8;
  private static final int VECTOR_GRID = 4;
  private static final AnalysisProviderDescriptor DESCRIPTOR = new AnalysisProviderDescriptor(
      "fixture-pixel",
      "fixture-pixel-v1",
      "pixel-descriptor",
      AnalysisArtifactManifest.empty(),
      false
  );

  private final Map<String, AnalysisTextEvidence> textEvidenceBySha;
  private final long maxPixels;

  public DeterministicPixelAnalysisProvider() {
    this(Map.of(), 40_000_000L);
  }

  public DeterministicPixelAnalysisProvider(
      Map<String, AnalysisTextEvidence> textEvidenceBySha,
      long maxPixels
  ) {
    this.textEvidenceBySha = Map.copyOf(Objects.requireNonNull(textEvidenceBySha, "textEvidenceBySha is required"));
    if (maxPixels <= 0L) {
      throw new IllegalArgumentException("maxPixels must be positive");
    }
    this.maxPixels = maxPixels;
  }

  @Override
  public AnalysisProviderDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public MediaAnalysisResult analyze(MediaAnalysisInput input, byte[] encodedImage) {
    Objects.requireNonNull(input, "input is required");
    Objects.requireNonNull(encodedImage, "encodedImage is required");
    if (!sha256(encodedImage).equals(input.inputSha256())) {
      throw new IllegalArgumentException("encodedImage does not match input SHA-256");
    }
    try {
      BufferedImage image = decodeWithinPixelLimit(encodedImage);
      AnalysisTextEvidence text = textEvidenceBySha.getOrDefault(
          input.inputSha256(), AnalysisTextEvidence.unknown("fixture-text-v1"));
      return new MediaAnalysisResult(
          input.assetId(),
          input.inputSha256(),
          input.profile(),
          DESCRIPTOR,
          image.getWidth(),
          image.getHeight(),
          averageHash(image),
          pixelVector(image),
          text,
          List.of(
              new AnalysisExplanation(
                  "provider",
                  "deterministic fixture pixel provider",
                  Map.of("providerId", DESCRIPTOR.providerId(), "version", DESCRIPTOR.version())),
              new AnalysisExplanation(
                  "text-evidence",
                  "text evidence is fixture-supplied and must not be treated as OCR",
                  Map.of("source", text.source(), "role", text.role().name()))
          )
      );
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("encodedImage cannot be decoded", ex);
    }
  }

  private BufferedImage decodeWithinPixelLimit(byte[] encodedImage) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encodedImage))) {
      if (input == null) {
        throw new IllegalArgumentException("encodedImage cannot be read");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IllegalArgumentException("encodedImage is not a supported image");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || ((long) width * height) > maxPixels) {
          throw new IllegalArgumentException("encodedImage exceeds configured pixel limit");
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw new IllegalArgumentException("encodedImage cannot be decoded");
        }
        return image;
      } finally {
        reader.dispose();
      }
    }
  }

  private long averageHash(BufferedImage image) {
    double[] values = new double[HASH_GRID * HASH_GRID];
    double average = 0.0d;
    for (int y = 0; y < HASH_GRID; y++) {
      for (int x = 0; x < HASH_GRID; x++) {
        double value = averageCell(image, x, y, HASH_GRID);
        values[y * HASH_GRID + x] = value;
        average += value;
      }
    }
    average /= values.length;
    long hash = 0L;
    for (int i = 0; i < values.length; i++) {
      if (values[i] >= average) {
        hash |= 1L << i;
      }
    }
    return hash;
  }

  private List<Double> pixelVector(BufferedImage image) {
    List<Double> values = new ArrayList<>(VECTOR_GRID * VECTOR_GRID * 3);
    for (int y = 0; y < VECTOR_GRID; y++) {
      for (int x = 0; x < VECTOR_GRID; x++) {
        long[] channels = new long[3];
        long count = 0L;
        int startX = (x * image.getWidth()) / VECTOR_GRID;
        int endX = Math.max(startX + 1, ((x + 1) * image.getWidth()) / VECTOR_GRID);
        int startY = (y * image.getHeight()) / VECTOR_GRID;
        int endY = Math.max(startY + 1, ((y + 1) * image.getHeight()) / VECTOR_GRID);
        endX = Math.min(endX, image.getWidth());
        endY = Math.min(endY, image.getHeight());
        for (int py = startY; py < endY; py++) {
          for (int px = startX; px < endX; px++) {
            int rgb = image.getRGB(px, py);
            channels[0] += (rgb >>> 16) & 0xff;
            channels[1] += (rgb >>> 8) & 0xff;
            channels[2] += rgb & 0xff;
            count++;
          }
        }
        for (long channel : channels) {
          values.add(round6(count == 0L ? 0.0d : ((double) channel / count) / 255.0d));
        }
      }
    }
    return values;
  }

  private double averageCell(BufferedImage image, int cellX, int cellY, int grid) {
    long red = 0L;
    long green = 0L;
    long blue = 0L;
    long count = 0L;
    int startX = (cellX * image.getWidth()) / grid;
    int endX = Math.max(startX + 1, ((cellX + 1) * image.getWidth()) / grid);
    int startY = (cellY * image.getHeight()) / grid;
    int endY = Math.max(startY + 1, ((cellY + 1) * image.getHeight()) / grid);
    endX = Math.min(endX, image.getWidth());
    endY = Math.min(endY, image.getHeight());
    for (int y = startY; y < endY; y++) {
      for (int x = startX; x < endX; x++) {
        int rgb = image.getRGB(x, y);
        red += (rgb >>> 16) & 0xff;
        green += (rgb >>> 8) & 0xff;
        blue += rgb & 0xff;
        count++;
      }
    }
    return count == 0L ? 0.0d : ((0.2126d * red) + (0.7152d * green) + (0.0722d * blue)) / count;
  }

  private double round6(double value) {
    return Math.round(value * 1_000_000d) / 1_000_000d;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
