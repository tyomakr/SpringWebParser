package ru.aikr.inet.parser.mlpublish;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MlPublishCommitResponse {
    private final int uploadedCount;
    private final int publishedCount;
    private final int postsPublished;
    private final int postsFailed;
}