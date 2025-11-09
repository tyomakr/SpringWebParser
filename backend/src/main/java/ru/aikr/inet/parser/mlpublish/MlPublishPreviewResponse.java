package ru.aikr.inet.parser.mlpublish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MlPublishPreviewResponse {
    private List<MlPublishPreviewItem> recommendations;
}

@Getter
@NoArgsConstructor
@AllArgsConstructor
class MlPublishPreviewItem {
    private String id;
    private String url;
    private double score;
    private String reason;
    private String recommendation;
}