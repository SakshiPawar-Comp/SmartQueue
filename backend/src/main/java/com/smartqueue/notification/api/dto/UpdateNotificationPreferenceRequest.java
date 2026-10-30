package com.smartqueue.notification.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateNotificationPreferenceRequest {

    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean pushEnabled;

    @Min(value = 1, message = "Threshold must be at least 1")
    @Max(value = 20, message = "Threshold cannot exceed 20")
    private Integer positionNearThreshold;

    public Boolean getInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(Boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }

    public Boolean getEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(Boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public Boolean getSmsEnabled() {
        return smsEnabled;
    }

    public void setSmsEnabled(Boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    public Boolean getPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(Boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public Integer getPositionNearThreshold() {
        return positionNearThreshold;
    }

    public void setPositionNearThreshold(Integer positionNearThreshold) {
        this.positionNearThreshold = positionNearThreshold;
    }
}
