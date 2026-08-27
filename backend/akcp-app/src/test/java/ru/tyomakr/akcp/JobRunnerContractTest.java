package ru.tyomakr.akcp;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobStatus;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.jobs.service.JobClaim;
import ru.tyomakr.akcp.jobs.service.JobExecutionResult;
import ru.tyomakr.akcp.jobs.service.JobHandler;
import ru.tyomakr.akcp.jobs.service.JobRunner;
import ru.tyomakr.akcp.jobs.service.JobService;

class JobRunnerContractTest {
  @Test
  void successfulHandlerPersistsDoneForItsClaim() {
    Job job = job();
    JobClaim claim = claim(job);
    JobService jobService = mock(JobService.class);
    JobHandler handler = mock(JobHandler.class);
    when(handler.type()).thenReturn(JobType.PUBLISH_VK);
    when(handler.handle(job)).thenReturn(Mono.just(JobExecutionResult.done()));
    givenClaim(jobService, job, claim);
    when(jobService.markDone(eq(claim), isNull())).thenReturn(Mono.just(true));

    new JobRunner(jobService, List.of(handler)).runTick();

    verify(jobService).markDone(claim, null);
  }

  @Test
  void handlerFailurePersistsFailedForItsClaim() {
    Job job = job();
    JobClaim claim = claim(job);
    JobService jobService = mock(JobService.class);
    JobHandler handler = mock(JobHandler.class);
    when(handler.type()).thenReturn(JobType.PUBLISH_VK);
    when(handler.handle(job)).thenReturn(Mono.error(new IllegalStateException("fixture failure")));
    givenClaim(jobService, job, claim);
    when(jobService.markFailed(claim, "fixture failure")).thenReturn(Mono.just(true));

    new JobRunner(jobService, List.of(handler)).runTick();

    verify(jobService).markFailed(claim, "fixture failure");
  }

  @Test
  void unknownHandlerOutcomeUsesUnknownState() {
    Job job = job();
    JobClaim claim = claim(job);
    JobService jobService = mock(JobService.class);
    JobHandler handler = mock(JobHandler.class);
    when(handler.type()).thenReturn(JobType.PUBLISH_VK);
    when(handler.handle(job)).thenReturn(
        Mono.just(JobExecutionResult.unknown("fixture-reference", "fixture timeout"))
    );
    givenClaim(jobService, job, claim);
    when(jobService.markUnknown(claim, "fixture-reference", "fixture timeout"))
        .thenReturn(Mono.just(true));

    new JobRunner(jobService, List.of(handler)).runTick();

    verify(jobService).markUnknown(claim, "fixture-reference", "fixture timeout");
  }

  @Test
  void failedClaimDoesNotInvokeHandler() {
    Job job = job();
    JobService jobService = mock(JobService.class);
    JobHandler handler = mock(JobHandler.class);
    when(handler.type()).thenReturn(JobType.PUBLISH_VK);
    when(jobService.fetchClaimable(10)).thenReturn(Flux.just(job));
    when(jobService.claimJob(job.id())).thenReturn(Mono.empty());

    new JobRunner(jobService, List.of(handler)).runTick();

    verify(handler, never()).handle(job);
  }

  private void givenClaim(JobService jobService, Job job, JobClaim claim) {
    when(jobService.fetchClaimable(10)).thenReturn(Flux.just(job));
    when(jobService.claimJob(job.id())).thenReturn(Mono.just(claim));
  }

  private JobClaim claim(Job job) {
    return new JobClaim(job, UUID.randomUUID(), Instant.EPOCH.plusSeconds(60));
  }

  private Job job() {
    return new Job(
        UUID.randomUUID(),
        JobType.PUBLISH_VK,
        JobStatus.QUEUED,
        "{}",
        Instant.EPOCH,
        Instant.EPOCH,
        null
    );
  }
}
