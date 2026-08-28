package ru.tyomakr.akcp.jobs.service;

import ru.tyomakr.akcp.core.model.JobStatus;

public record JobExecutionResult(
    JobStatus status,
    String externalResult,
    String detail
) {
  public JobExecutionResult {
    if (status != JobStatus.DONE && status != JobStatus.UNKNOWN) {
      throw new IllegalArgumentException("Handler result must be DONE or UNKNOWN");
    }
  }

  public static JobExecutionResult done() {
    return new JobExecutionResult(JobStatus.DONE, null, null);
  }

  public static JobExecutionResult done(String externalResult) {
    return new JobExecutionResult(JobStatus.DONE, externalResult, null);
  }

  public static JobExecutionResult unknown(String externalResult, String detail) {
    return new JobExecutionResult(JobStatus.UNKNOWN, externalResult, detail);
  }
}
