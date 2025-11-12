package ru.aikr.inet.parser.mlfeedback.model;

import java.time.Instant;

public class MlFeedbackRecord {

    private Long id;
    private Long candidateId;
    private String url;
    private String hash;
    private String decision;
    private Double score;
    private String reason;
    private String zone;
    private Instant createdAt;

    public MlFeedbackRecord() {
    }

    public MlFeedbackRecord(Long candidateId, String url, String hash, String decision, Double score, String reason,
                            String zone) {
        this.candidateId = candidateId;
        this.url = url;
        this.hash = hash;
        this.decision = decision;
        this.score = score;
        this.reason = reason;
        this.zone = zone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
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

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
