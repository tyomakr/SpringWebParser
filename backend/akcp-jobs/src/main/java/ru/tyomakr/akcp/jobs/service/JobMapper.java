package ru.tyomakr.akcp.jobs.service;

import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobStatus;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.jobs.persistence.JobRow;

final class JobMapper {
  private JobMapper() {
  }

  static Job toJob(JobRow row) {
    return new Job(
        row.id(),
        JobType.valueOf(row.type()),
        JobStatus.valueOf(row.status()),
        row.payload(),
        row.createdAt(),
        row.updatedAt(),
        row.lastError()
    );
  }
}
