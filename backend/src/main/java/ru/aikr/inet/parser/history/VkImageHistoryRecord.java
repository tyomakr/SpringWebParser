package ru.aikr.inet.parser.history;

import java.time.Instant;

public class VkImageHistoryRecord {

    private Long id;
    private Long postId;
    private String url;
    private String hash;
    private Instant createdAt;
    private Instant syncedAt;
    private String mlDecision;
    private Double mlScore;
    private String mlReason;

    public VkImageHistoryRecord() {
    }

    public VkImageHistoryRecord(Long postId, String url, String hash, Instant createdAt) {
        this.postId = postId;
        this.url = url;
        this.hash = hash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getMlDecision() {
        return mlDecision;
    }

    public void setMlDecision(String mlDecision) {
        this.mlDecision = mlDecision;
    }

    public Double getMlScore() {
        return mlScore;
    }

    public void setMlScore(Double mlScore) {
        this.mlScore = mlScore;
    }

    public String getMlReason() {
        return mlReason;
    }

    public void setMlReason(String mlReason) {
        this.mlReason = mlReason;
    }
}
