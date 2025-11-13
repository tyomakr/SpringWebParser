package ru.aikr.inet.parser.history.model;

import java.util.List;

public record PageDTO<T>(
        List<T> items,
        long total,
        int limit,
        int offset
) {}
