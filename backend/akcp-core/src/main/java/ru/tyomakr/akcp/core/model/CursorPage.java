package ru.tyomakr.akcp.core.model;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {
}
