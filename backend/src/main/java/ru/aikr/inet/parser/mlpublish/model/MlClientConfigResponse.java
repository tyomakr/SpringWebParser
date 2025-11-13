package ru.aikr.inet.parser.mlpublish.model;

public record MlClientConfigResponse(
        boolean apiKeyConfigured,
        boolean requireApiKey,
        int maxBatchSize,
        MlConfigResponse mlServiceConfig
) {}
