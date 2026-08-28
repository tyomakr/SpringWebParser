package ru.tyomakr.akcp.core.content;

public enum HistoryTransitionOutcome {
  PROMOTE,
  REACTIVATE,
  KEEP_ACTIVE,
  DEACTIVATE,
  KEEP_INACTIVE,
  IGNORE_UNCONFIRMED
}
