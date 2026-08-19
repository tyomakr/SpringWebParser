package ru.tyomakr.akcp.library.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.AnalysisItemOutcomeStatus;
import ru.tyomakr.akcp.core.content.AnalysisRunStore;
import ru.tyomakr.akcp.core.content.AnalysisRunWriteResult;
import ru.tyomakr.akcp.core.content.AnalysisWorkItem;
import ru.tyomakr.akcp.core.content.AnalysisWorkSource;
import ru.tyomakr.akcp.core.content.AnalysisWorkerReport;
import ru.tyomakr.akcp.core.content.MediaAnalysisInput;
import ru.tyomakr.akcp.core.content.MediaAnalysisResult;
import ru.tyomakr.akcp.core.content.StorageReference;
import ru.tyomakr.akcp.core.content.StoredMedia;
import ru.tyomakr.akcp.core.content.AnalysisProfileVersion;
import ru.tyomakr.akcp.library.media.storage.InMemoryStorageAdapter;

class BoundedAnalysisWorkerTest {
  @Test
  void processesSequentiallyAndReusesTheSameIdempotencyKey() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    byte[] bytes = "fixture-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    StoredMedia stored = storage.store(new ByteArrayInputStream(bytes));
    MediaAnalysisInput input = input(stored.reference());
    QueueSource source = new QueueSource(List.of(new AnalysisWorkItem(input)));
    RecordingStore runs = new RecordingStore();
    DeterministicPixelAnalysisProvider provider = new DeterministicPixelAnalysisProvider();

    // The fixture provider rejects non-image bytes, so use a provider that proves the worker seam.
    ru.tyomakr.akcp.core.content.MediaAnalysisProvider fake = new FakeProvider(provider.descriptor());
    BoundedAnalysisWorker worker = new BoundedAnalysisWorker(storage, source, fake, runs, 1024L);

    AnalysisWorkerReport first = worker.run(1);
    assertThat(first.created()).isEqualTo(1L);
    assertThat(first.failed()).isZero();
    assertThat(runs.results).hasSize(1);

    source.reset(List.of(new AnalysisWorkItem(input)));
    AnalysisWorkerReport second = worker.run(1);
    assertThat(second.reused()).isEqualTo(1L);
    assertThat(runs.results).hasSize(1);
  }

  @Test
  void failedHashOrOversizeNeverWritesPartialResult() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    StoredMedia stored = storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    RecordingStore runs = new RecordingStore();
    BoundedAnalysisWorker worker = new BoundedAnalysisWorker(
        storage,
        new QueueSource(List.of(new AnalysisWorkItem(input(stored.reference())))),
        new FakeProvider(new DeterministicPixelAnalysisProvider().descriptor()),
        runs,
        2L);

    AnalysisWorkerReport report = worker.run(1);
    assertThat(report.outcomes()).singleElement().satisfies(outcome -> {
      assertThat(outcome.status()).isEqualTo(AnalysisItemOutcomeStatus.FAILED);
      assertThat(outcome.detail()).contains("byte limit");
    });
    assertThat(runs.results).isEmpty();
  }

  @Test
  void sourceLimitIsEnforcedBeforeProcessing() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    RecordingStore runs = new RecordingStore();
    AnalysisWorkSource oversized = limit -> List.of(
        new AnalysisWorkItem(input(StorageReference.fromSha256("a".repeat(64)))),
        new AnalysisWorkItem(input(StorageReference.fromSha256("b".repeat(64)))));

    assertThatThrownBy(() -> new BoundedAnalysisWorker(
        storage,
        oversized,
        new FakeProvider(new DeterministicPixelAnalysisProvider().descriptor()),
        runs,
        1024L).run(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit");
    assertThat(runs.results).isEmpty();
  }

  private MediaAnalysisInput input(StorageReference reference) {
    return new MediaAnalysisInput(
        UUID.randomUUID(),
        reference.sha256(),
        reference,
        new AnalysisProfileVersion("fixture-v1"));
  }

  private static final class QueueSource implements AnalysisWorkSource {
    private List<AnalysisWorkItem> items;

    private QueueSource(List<AnalysisWorkItem> items) {
      reset(items);
    }

    @Override
    public List<AnalysisWorkItem> next(int limit) {
      return List.copyOf(items);
    }

    private void reset(List<AnalysisWorkItem> replacement) {
      this.items = List.copyOf(replacement);
    }
  }

  private static final class RecordingStore implements AnalysisRunStore {
    private final List<MediaAnalysisResult> results = new ArrayList<>();

    @Override
    public CompletionStage<AnalysisRunWriteResult> saveIfAbsent(MediaAnalysisResult result) {
      boolean exists = results.stream().anyMatch(existing ->
          existing.assetId().equals(result.assetId())
              && existing.inputSha256().equals(result.inputSha256())
              && existing.profile().equals(result.profile()));
      if (!exists) {
        results.add(result);
        return CompletableFuture.completedFuture(AnalysisRunWriteResult.CREATED);
      }
      return CompletableFuture.completedFuture(AnalysisRunWriteResult.REUSED);
    }
  }

  private static final class FakeProvider implements ru.tyomakr.akcp.core.content.MediaAnalysisProvider {
    private final ru.tyomakr.akcp.core.content.AnalysisProviderDescriptor descriptor;

    private FakeProvider(ru.tyomakr.akcp.core.content.AnalysisProviderDescriptor descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ru.tyomakr.akcp.core.content.AnalysisProviderDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public MediaAnalysisResult analyze(MediaAnalysisInput input, byte[] encodedImage) {
      return new MediaAnalysisResult(
          input.assetId(),
          input.inputSha256(),
          input.profile(),
          descriptor,
          1,
          1,
          0L,
          List.of(0.0d),
          ru.tyomakr.akcp.core.content.AnalysisTextEvidence.unknown("fixture"),
          List.of());
    }
  }
}
