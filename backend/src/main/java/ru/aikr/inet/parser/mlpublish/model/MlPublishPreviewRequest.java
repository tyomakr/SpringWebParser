package ru.aikr.inet.parser.mlpublish.model;

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
