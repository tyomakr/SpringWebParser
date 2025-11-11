package ru.aikr.inet.parser.mlpublish;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MlPublishCommitRequest {
    @NotEmpty
    private List<@Valid MlPublishCommitItem> images;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class MlPublishCommitItem {
    private String id;
    private String url;
    private boolean publish;
    private RecommendationDecision decision;
    private Double score;
    private String reason;
}
