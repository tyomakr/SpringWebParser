package ru.tyomakr.akcp.publishing.vk.dto;

import java.time.Instant;
import java.util.UUID;
import ru.tyomakr.akcp.core.model.Job;

public record PublishJobResponse(
    UUID id,
    String type,
    String status,
    Instant createdAt
) {
  public static PublishJobResponse fromJob(Job job) {
    return new PublishJobResponse(job.id(), job.type().name(), job.status().name(), job.createdAt());
  }
}
