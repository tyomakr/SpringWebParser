package ru.aikr.inet.parser.mlpublish.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MlPublishPreviewItem {
    private String id;
    private String url;
    private double score;
    private String reason;
    private String recommendation;
}
