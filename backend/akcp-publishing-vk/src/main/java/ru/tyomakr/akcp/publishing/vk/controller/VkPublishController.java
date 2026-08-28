package ru.tyomakr.akcp.publishing.vk.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.publishing.vk.dto.PublishJobResponse;
import ru.tyomakr.akcp.publishing.vk.service.VkPublishService;

@RestController
@RequestMapping("/api/publish/vk")
public class VkPublishController {
  private final VkPublishService vkPublishService;

  public VkPublishController(VkPublishService vkPublishService) {
    this.vkPublishService = vkPublishService;
  }

  @PostMapping("/{itemId}")
  public Mono<PublishJobResponse> publish(@PathVariable UUID itemId) {
    return vkPublishService.queuePublish(itemId)
        .map(PublishJobResponse::fromJob);
  }
}
