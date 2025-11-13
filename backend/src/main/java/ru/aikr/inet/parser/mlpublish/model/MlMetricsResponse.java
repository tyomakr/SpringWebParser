package ru.aikr.inet.parser.mlpublish.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlMetricsResponse(long indexSize) {
}
