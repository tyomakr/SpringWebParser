package ru.aikr.inet.parser.mlpublish;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MlPublishPreviewRequest {
    @NotEmpty
    private List<@Valid MlPublishCandidate> images;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class MlPublishCandidate {
    private String id;
    private String url;
}