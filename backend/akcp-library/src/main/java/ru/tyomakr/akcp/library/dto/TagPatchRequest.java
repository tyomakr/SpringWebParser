package ru.tyomakr.akcp.library.dto;

import java.util.List;

public record TagPatchRequest(List<String> add, List<String> remove) {
}
