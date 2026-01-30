package ru.tyomakr.akcp.publishing.vk.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.library.service.ItemService;
import ru.tyomakr.akcp.jobs.service.JobService;

@Service
public class VkPublishService {
  private final ItemService itemService;
  private final JobService jobService;

  public VkPublishService(ItemService itemService, JobService jobService) {
    this.itemService = itemService;
    this.jobService = jobService;
  }

  public Mono<Job> queuePublish(UUID itemId) {
    return itemService.getItem(itemId)
        .then(jobService.createJob(JobType.PUBLISH_VK, payload(itemId)));
  }

  private String payload(UUID itemId) {
    return "{\"itemId\":\"" + itemId + "\"}";
  }
}
