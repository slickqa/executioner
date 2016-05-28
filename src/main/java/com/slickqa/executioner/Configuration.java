package com.slickqa.executioner;

/**
 * Configuration for slick.
 */
public interface Configuration {
    String getPathToAgentConfigs();
    int getWorkQueueSize();
    int getWorkQueueBroadcastInterval();
}
