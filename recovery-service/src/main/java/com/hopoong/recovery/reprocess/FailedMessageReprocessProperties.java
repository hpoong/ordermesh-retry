package com.hopoong.recovery.reprocess;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recovery.reprocess")
public class FailedMessageReprocessProperties {

    private boolean enabled = false;
    private long fixedDelayMs = 60000;
    private long minWaitMinutes = 10;
    private int batchSize = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getMinWaitMinutes() {
        return minWaitMinutes;
    }

    public void setMinWaitMinutes(long minWaitMinutes) {
        this.minWaitMinutes = minWaitMinutes;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
