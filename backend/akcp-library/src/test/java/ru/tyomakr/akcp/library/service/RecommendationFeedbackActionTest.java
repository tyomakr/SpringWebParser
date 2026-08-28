package ru.tyomakr.akcp.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RecommendationFeedbackActionTest {
  @Test
  void exposesOnlyHumanModerationActions() {
    assertThat(RecommendationFeedbackAction.values())
        .containsExactly(
            RecommendationFeedbackAction.APPROVE,
            RecommendationFeedbackAction.REJECT,
            RecommendationFeedbackAction.SKIP
        );
  }

  @Test
  void parsesCaseAndWhitespaceButRejectsPublishing() {
    assertThat(RecommendationFeedbackAction.parse(" approve "))
        .isEqualTo(RecommendationFeedbackAction.APPROVE);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RecommendationFeedbackAction.parse("PUBLISH"));
  }
}
