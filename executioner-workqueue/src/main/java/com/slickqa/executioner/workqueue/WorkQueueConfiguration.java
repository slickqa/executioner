package com.slickqa.executioner.workqueue;

/**
 * WorkQueueConfiguration for slick.
 */
public interface WorkQueueConfiguration {
    int getWorkQueueSize();
    int getWorkQueueBroadcastInterval();
}

