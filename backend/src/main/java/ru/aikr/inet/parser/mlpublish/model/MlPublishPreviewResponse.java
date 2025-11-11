package ru.aikr.inet.parser.mlpublish.model;

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
