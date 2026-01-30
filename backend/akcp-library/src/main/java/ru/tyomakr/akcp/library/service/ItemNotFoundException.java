package ru.tyomakr.akcp.library.service;

import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {
  public ItemNotFoundException(UUID id) {
    super("Item not found: " + id);
  }
}
