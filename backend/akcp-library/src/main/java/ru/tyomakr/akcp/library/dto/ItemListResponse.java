package ru.tyomakr.akcp.library.dto;

import java.util.List;

public record ItemListResponse(List<ItemResponse> items, String nextCursor) {
}
