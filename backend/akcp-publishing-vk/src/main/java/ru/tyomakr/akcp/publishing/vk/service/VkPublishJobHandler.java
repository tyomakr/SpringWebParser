package ru.tyomakr.akcp.publishing.vk.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.jobs.service.JobHandler;
import ru.tyomakr.akcp.jobs.service.JobExecutionResult;

@Component
public class VkPublishJobHandler implements JobHandler {
  private final VkPublisher vkPublisher;

  public VkPublishJobHandler(VkPublisher vkPublisher) {
    this.vkPublisher = vkPublisher;
  }

  @Override
  public JobType type() {
    return JobType.PUBLISH_VK;
  }

  @Override
  public Mono<JobExecutionResult> handle(Job job) {
    UUID itemId = extractItemId(job.payload());
    return vkPublisher.publish(itemId).thenReturn(JobExecutionResult.done());
  }

  private UUID extractItemId(String payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Job payload missing itemId");
    }
    int index = payload.indexOf("\"itemId\"");
    if (index < 0) {
      throw new IllegalArgumentException("Job payload missing itemId");
    }
    int start = payload.indexOf('"', index + 8);
    if (start < 0) {
      throw new IllegalArgumentException("Job payload missing itemId");
    }
    int end = payload.indexOf('"', start + 1);
    if (end < 0) {
      throw new IllegalArgumentException("Job payload missing itemId");
    }
    return UUID.fromString(payload.substring(start + 1, end));
  }
}
