package com.slickqa.executioner.slickv4connector;

/**
 * Configuration for slickv4 connector
 */
public interface Slickv4Configuration {
    String getSlickUrl();
    String getProjectName();
    String getExecutionerAgentName();
    int getPollingInterval();
    int getQueueSizeLowerBound();
    int getSimultaneousFetchLimit();
}
