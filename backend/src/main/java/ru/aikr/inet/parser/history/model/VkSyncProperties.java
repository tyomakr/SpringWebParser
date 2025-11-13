package ru.aikr.inet.parser.history.model;

import java.time.Duration;

public class VkSyncProperties {

    private boolean enabled = false;
    private String cron = "0 */15 * * * *";
    private int pageSize = 100;
    private int pageLimit = 10;
    private Duration rateLimit = Duration.ofMillis(350);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPageLimit() {
        return pageLimit;
    }

    public void setPageLimit(int pageLimit) {
        this.pageLimit = pageLimit;
    }

    public Duration getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Duration rateLimit) {
        this.rateLimit = rateLimit;
    }
}
