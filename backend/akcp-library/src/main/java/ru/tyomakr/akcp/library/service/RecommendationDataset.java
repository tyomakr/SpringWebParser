package ru.tyomakr.akcp.library.service;

public enum RecommendationDataset {
  CANDIDATE,
  VK_WALL;

  public static RecommendationDataset parse(String raw, RecommendationDataset fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    return RecommendationDataset.valueOf(raw.trim().toUpperCase());
  }
}
