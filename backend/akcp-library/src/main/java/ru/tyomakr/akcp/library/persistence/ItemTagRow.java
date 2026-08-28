package ru.tyomakr.akcp.library.persistence;

import java.util.UUID;

public record ItemTagRow(UUID itemId, TagRow tag) {
}
