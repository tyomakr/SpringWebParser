package ru.tyomakr.akcp.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecommendationPropertiesTest {
  @Test
  void keepsTextDominantThresholdBoundedAndConfigurable() {
    RecommendationProperties properties = new RecommendationProperties();

    assertThat(properties.getTextDominantThreshold()).isEqualTo(0.65d);
    properties.setTextDominantThreshold(0.8d);
    assertThat(properties.getTextDominantThreshold()).isEqualTo(0.8d);
    properties.setTextDominantThreshold(1.5d);
    assertThat(properties.getTextDominantThreshold()).isEqualTo(0.8d);
  }
}
