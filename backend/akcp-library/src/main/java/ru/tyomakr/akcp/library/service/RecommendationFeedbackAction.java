package ru.tyomakr.akcp.library.service;

public enum RecommendationFeedbackAction {
  APPROVE,
  REJECT,
  SKIP;

  public static RecommendationFeedbackAction parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("action is required");
    }
    return RecommendationFeedbackAction.valueOf(raw.trim().toUpperCase());
  }
}
