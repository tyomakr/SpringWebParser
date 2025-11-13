package ru.aikr.inet.parser.mlpublish.model;

public record MlStatusResponse(
        boolean mlReachable,
        Long indexSize,
        MlConfigResponse config,
        String error
) {}
