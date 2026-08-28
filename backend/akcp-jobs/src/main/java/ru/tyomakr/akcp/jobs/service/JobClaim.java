package ru.tyomakr.akcp.jobs.service;

import java.time.Instant;
import java.util.UUID;
import ru.tyomakr.akcp.core.model.Job;

public record JobClaim(
    Job job,
    UUID token,
    Instant leaseUntil
) {
}
