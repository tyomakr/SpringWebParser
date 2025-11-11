package ru.aikr.inet.parser.mlpublish.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MlPublishCommitItem {
    private String id;
    private String url;
    private boolean publish;
    private RecommendationDecision decision;
    private Double score;
    private String reason;
}
