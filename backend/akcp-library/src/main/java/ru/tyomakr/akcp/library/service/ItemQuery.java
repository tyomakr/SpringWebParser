package ru.tyomakr.akcp.library.service;

import java.time.Instant;

public record ItemQuery(
    Instant from,
    Instant to,
    String tag,
    Integer limit,
    String cursor
) {
}
