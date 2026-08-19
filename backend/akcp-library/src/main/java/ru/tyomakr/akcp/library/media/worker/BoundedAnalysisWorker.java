package ru.tyomakr.akcp.library.media.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ru.tyomakr.akcp.core.content.AnalysisItemOutcome;
import ru.tyomakr.akcp.core.content.AnalysisItemOutcomeStatus;
import ru.tyomakr.akcp.core.content.AnalysisProviderDescriptor;
import ru.tyomakr.akcp.core.content.AnalysisRunStore;
import ru.tyomakr.akcp.core.content.AnalysisRunWriteResult;
import ru.tyomakr.akcp.core.content.AnalysisWorkItem;
import ru.tyomakr.akcp.core.content.AnalysisWorkSource;
import ru.tyomakr.akcp.core.content.AnalysisWorkerReport;
import ru.tyomakr.akcp.core.content.MediaAnalysisInput;
import ru.tyomakr.akcp.core.content.MediaAnalysisProvider;
import ru.tyomakr.akcp.core.content.MediaAnalysisResult;
import ru.tyomakr.akcp.core.content.StoragePort;

/**
 * Synchronous, one-shot worker boundary. It deliberately processes one item at a time and is not
 * suitable for a WebFlux request thread. A future launcher may run this class as a bounded process.
 */
public final class BoundedAnalysisWorker {
  private final StoragePort storage;
  private final AnalysisWorkSource source;
  private final MediaAnalysisProvider provider;
  private final AnalysisRunStore runStore;
  private final long maxEncodedBytes;

  public BoundedAnalysisWorker(
      StoragePort storage,
      AnalysisWorkSource source,
      MediaAnalysisProvider provider,
      AnalysisRunStore runStore,
      long maxEncodedBytes
  ) {
    this.storage = Objects.requireNonNull(storage, "storage is required");
    this.source = Objects.requireNonNull(source, "source is required");
    this.provider = Objects.requireNonNull(provider, "provider is required");
    this.runStore = Objects.requireNonNull(runStore, "runStore is required");
    if (maxEncodedBytes <= 0L) {
      throw new IllegalArgumentException("maxEncodedBytes must be positive");
    }
    this.maxEncodedBytes = maxEncodedBytes;
  }

  public AnalysisWorkerReport run(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    List<AnalysisWorkItem> items = Objects.requireNonNull(source.next(limit), "source returned null");
    if (items.size() > limit) {
      throw new IllegalArgumentException("source returned more items than requested limit");
    }
    Set<String> seenKeys = new HashSet<>();
    List<AnalysisItemOutcome> outcomes = new ArrayList<>();
    for (AnalysisWorkItem item : items) {
      Objects.requireNonNull(item, "source returned null work item");
      if (!seenKeys.add(item.idempotencyKey())) {
        throw new IllegalArgumentException("source returned duplicate analysis key: " + item.idempotencyKey());
      }
      outcomes.add(process(item));
    }
    return new AnalysisWorkerReport(limit, outcomes);
  }

  private AnalysisItemOutcome process(AnalysisWorkItem item) {
    MediaAnalysisInput input = item.input();
    try {
      byte[] bytes = readBounded(input, maxEncodedBytes);
      String actualSha = sha256(bytes);
      if (!actualSha.equals(input.inputSha256())) {
        return failed(item, "input SHA-256 does not match catalog identity");
      }
      MediaAnalysisResult result = Objects.requireNonNull(
          provider.analyze(input, bytes), "provider returned null result");
      validateResult(input, result);
      AnalysisRunWriteResult writeResult = Objects.requireNonNull(
          runStore.saveIfAbsent(result), "run store returned null stage")
          .toCompletableFuture()
          .join();
      return new AnalysisItemOutcome(
          item.assetId(),
          writeResult == AnalysisRunWriteResult.CREATED
              ? AnalysisItemOutcomeStatus.CREATED
              : AnalysisItemOutcomeStatus.REUSED,
          writeResult.name().toLowerCase(java.util.Locale.ROOT));
    } catch (Exception ex) {
      String detail = ex.getMessage() == null || ex.getMessage().isBlank()
          ? ex.getClass().getSimpleName()
          : ex.getMessage();
      return failed(item, detail);
    }
  }

  private byte[] readBounded(MediaAnalysisInput input, long maxBytes) throws IOException {
    try (InputStream content = storage.open(input.storageReference())) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      long total = 0L;
      int read;
      while ((read = content.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new IOException("encoded image exceeds configured byte limit");
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private void validateResult(MediaAnalysisInput input, MediaAnalysisResult result) {
    if (!input.assetId().equals(result.assetId())
        || !input.inputSha256().equals(result.inputSha256())
        || !input.profile().equals(result.profile())) {
      throw new IllegalArgumentException("provider result identity does not match work item");
    }
    AnalysisProviderDescriptor descriptor = result.provider();
    if (!descriptor.equals(provider.descriptor())) {
      throw new IllegalArgumentException("provider result descriptor does not match active provider");
    }
  }

  private AnalysisItemOutcome failed(AnalysisWorkItem item, String detail) {
    return new AnalysisItemOutcome(item.assetId(), AnalysisItemOutcomeStatus.FAILED, detail);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
