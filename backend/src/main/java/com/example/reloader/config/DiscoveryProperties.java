package com.example.reloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.discovery")
public class DiscoveryProperties {
    private String site = "default";
    // Environment selection: e.g. "qa" or "prod"; when set this determines which db entry from dbconnections.json to use
    private String environment = "qa";
    private int senderId = 22;
    private String startDate;
    private String endDate;
    private String testerType;
    private String dataType;
    private String testPhase;
    private String location;
    private boolean writeListFile = false;
    private int numberOfDataToSend = 300;
    private int countLimitTrigger = 600;
    private String cron = "0 */5 * * * *";
    // Notification settings
    private String notifyRecipient;
    private boolean notifyAttachList = false;

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getTesterType() { return testerType; }
    public void setTesterType(String testerType) { this.testerType = testerType; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getTestPhase() { return testPhase; }
    public void setTestPhase(String testPhase) { this.testPhase = testPhase; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isWriteListFile() { return writeListFile; }
    public void setWriteListFile(boolean writeListFile) { this.writeListFile = writeListFile; }
    public int getNumberOfDataToSend() { return numberOfDataToSend; }
    public void setNumberOfDataToSend(int numberOfDataToSend) { this.numberOfDataToSend = numberOfDataToSend; }
    public int getCountLimitTrigger() { return countLimitTrigger; }
    public void setCountLimitTrigger(int countLimitTrigger) { this.countLimitTrigger = countLimitTrigger; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getNotifyRecipient() { return notifyRecipient; }
    public void setNotifyRecipient(String notifyRecipient) { this.notifyRecipient = notifyRecipient; }
    public boolean isNotifyAttachList() { return notifyAttachList; }
    public void setNotifyAttachList(boolean notifyAttachList) { this.notifyAttachList = notifyAttachList; }
}
