package ru.tyomakr.akcp.library.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.tyomakr.akcp.library.persistence.RecommendationAttachmentSourceRow;
import ru.tyomakr.akcp.library.persistence.RecommendationFeatureRow;

@Component
public class RecommendationFeatureExtractor {
  private static final int EMBEDDING_DIMENSION = 32;
  private static final List<String> TEXT_DOMINANT_KEYWORDS = List.of(
      "quote", "quotes", "text", "txt", "meme", "motivat", "demotivat",
      "цитат", "текст", "надпись", "aforizm", "status"
  );

  public RecommendationFeatureRow extract(
      RecommendationAttachmentSourceRow source,
      RecommendationDataset dataset,
    Instant now
  ) {
    if (source.analysisEmbeddingJson() != null
        && source.analysisSha256() != null
        && source.analysisPhash() != null
        && source.analysisTextRatio() != null
        && source.analysisTextDominant() != null
        && source.analysisVersion() != null) {
      return new RecommendationFeatureRow(
          UUID.randomUUID(),
          dataset.name(),
          source.attachmentId(),
          source.itemId(),
          normalize(source.imageUrl()),
          source.analysisSha256(),
          source.analysisPhash(),
          source.analysisEmbeddingJson(),
          source.analysisTextRatio(),
          source.analysisTextDominant(),
          now,
          now,
          source.analysisVersion(),
          source.analysisExplanationJson()
      );
    }
    String normalizedUrl = normalize(source.imageUrl());
    String sha256 = sha256Hex(normalizedUrl);
    long pHash = pseudoPhash(sha256);
    TextSignal textSignal = estimateTextSignal(normalizedUrl);
    List<Double> embedding = buildEmbedding(
        normalizedUrl,
        normalize(source.sourceType()),
        source.tags(),
        source.itemCreatedAt()
    );

    return new RecommendationFeatureRow(
        UUID.randomUUID(),
        dataset.name(),
        source.attachmentId(),
        source.itemId(),
        normalizedUrl,
        sha256,
        pHash,
        toEmbeddingJson(embedding),
        textSignal.ratio(),
        textSignal.dominant(),
        now,
        now,
        "legacy-url-v1",
        "{\"warning\":\"no persisted byte-analysis run\"}"
    );
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte current : hash) {
        sb.append(String.format("%02x", current));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute sha256", ex);
    }
  }

  private long pseudoPhash(String sha256Hex) {
    byte[] bytes = hexToBytes(sha256Hex);
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return buffer.getLong();
  }

  private byte[] hexToBytes(String hex) {
    int length = hex.length();
    byte[] output = new byte[length / 2];
    for (int i = 0; i < length; i += 2) {
      output[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
    }
    return output;
  }

  private TextSignal estimateTextSignal(String normalizedUrl) {
    String lower = normalizedUrl.toLowerCase(Locale.ROOT);
    for (String keyword : TEXT_DOMINANT_KEYWORDS) {
      if (lower.contains(keyword)) {
        return new TextSignal(0.95d, true);
      }
    }
    String fileName = lower.substring(lower.lastIndexOf('/') + 1);
    int letters = 0;
    int total = 0;
    for (int i = 0; i < fileName.length(); i++) {
      char ch = fileName.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        total++;
        if (Character.isLetter(ch)) {
          letters++;
        }
      }
    }
    if (total == 0) {
      return new TextSignal(0.0d, false);
    }
    double ratio = Math.min((double) letters / (double) total, 0.85d);
    boolean dominant = ratio > 0.70d && fileName.length() > 20;
    return new TextSignal(round4(ratio), dominant);
  }

  private List<Double> buildEmbedding(String imageUrl, String sourceType, List<String> tags, Instant createdAt) {
    double[] values = new double[EMBEDDING_DIMENSION];
    applyTokens(values, tokenize(imageUrl), 1.0d);
    applyTokens(values, tokenize(sourceType), 0.6d);
    if (tags != null) {
      for (String tag : tags) {
        applyTokens(values, tokenize(tag), 0.9d);
      }
    }
    if (createdAt != null) {
      long days = Math.max(0L, createdAt.getEpochSecond() / 86400L);
      int bucket = (int) (days % EMBEDDING_DIMENSION);
      values[bucket] += 0.5d;
    }
    normalizeVector(values);
    List<Double> embedding = new ArrayList<>(EMBEDDING_DIMENSION);
    for (double value : values) {
      embedding.add(round4(value));
    }
    return embedding;
  }

  private List<String> tokenize(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String prepared = raw
        .toLowerCase(Locale.ROOT)
        .replace("https://", " ")
        .replace("http://", " ")
        .replaceAll("[^\\p{L}\\p{Nd}]+", " ");
    String[] chunks = prepared.trim().split("\\s+");
    List<String> tokens = new ArrayList<>();
    for (String chunk : chunks) {
      if (!chunk.isBlank()) {
        tokens.add(chunk);
      }
    }
    return tokens;
  }

  private void applyTokens(double[] values, List<String> tokens, double weight) {
    for (String token : tokens) {
      int index = Math.floorMod(token.hashCode(), values.length);
      values[index] += weight;
    }
  }

  private void normalizeVector(double[] values) {
    double norm = 0.0d;
    for (double value : values) {
      norm += value * value;
    }
    if (norm == 0.0d) {
      return;
    }
    double divider = Math.sqrt(norm);
    for (int i = 0; i < values.length; i++) {
      values[i] /= divider;
    }
  }

  private String toEmbeddingJson(List<Double> embedding) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < embedding.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(embedding.get(i));
    }
    sb.append(']');
    return sb.toString();
  }

  private double round4(double value) {
    return Math.round(value * 10_000d) / 10_000d;
  }

  private record TextSignal(double ratio, boolean dominant) {
  }
}
