package ru.tyomakr.akcp.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisWorkerContractsTest {
  @Test
  void textPresenceAloneDoesNotMakeEvidenceDominant() {
    AnalysisTextEvidence supporting = new AnalysisTextEvidence(
        0.90d,
        AnalysisTextRole.SUPPORTING,
        0.9d,
        "ocr-fixture-v1",
        "fixture");
    AnalysisTextEvidence primary = new AnalysisTextEvidence(
        0.65d,
        AnalysisTextRole.PRIMARY,
        0.9d,
        "ocr-fixture-v1",
        "fixture");

    assertFalse(supporting.isDominant(0.65d));
    assertTrue(primary.isDominant(0.65d));
    assertFalse(AnalysisTextEvidence.unknown("ocr-v1").isDominant(0.1d));
  }

  @Test
  void unknownEvidenceCannotPretendToHaveAnAreaRatio() {
    assertThrows(IllegalArgumentException.class, () -> new AnalysisTextEvidence(
        0.2d,
        AnalysisTextRole.UNKNOWN,
        0.0d,
        "ocr-v1",
        "unknown"));
  }

  @Test
  void artifactManifestRejectsNetworkLocationsAndCopiesCollections() {
    assertThrows(IllegalArgumentException.class, () -> new AnalysisArtifact(
        "model",
        "1",
        "Apache-2.0",
        "a".repeat(64),
        10L,
        "https://example.invalid/model"));

    AnalysisExplanation explanation = new AnalysisExplanation("code", "message", Map.of("key", "value"));
    assertEquals("value", explanation.evidence().get("key"));
    assertTrue(AnalysisArtifactManifest.empty().artifacts().isEmpty());
  }

  @Test
  void providerDescriptorIsFailClosedForNetworkAccess() {
    assertThrows(IllegalArgumentException.class, () -> new AnalysisProviderDescriptor(
        "remote",
        "1",
        "embedding",
        AnalysisArtifactManifest.empty(),
        true));

    MediaAnalysisInput input = new MediaAnalysisInput(
        UUID.randomUUID(),
        "b".repeat(64),
        StorageReference.fromSha256("b".repeat(64)),
        new AnalysisProfileVersion("fixture-v1"));
    MediaAnalysisResult result = new MediaAnalysisResult(
        input.assetId(),
        input.inputSha256(),
        input.profile(),
        new AnalysisProviderDescriptor(
            "fixture",
            "1",
            "descriptor",
            AnalysisArtifactManifest.empty(),
            false),
        1,
        1,
        0L,
        List.of(0.0d),
        AnalysisTextEvidence.unknown("fixture-text-v1"),
        List.of());
    assertEquals(List.of(0.0d), result.visualVector());
  }
}
