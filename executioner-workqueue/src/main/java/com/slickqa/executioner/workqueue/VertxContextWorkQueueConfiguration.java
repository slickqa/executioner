package com.slickqa.executioner.workqueue;

import io.vertx.core.json.JsonObject;

/**
 * Slick configuration that uses Vertx Context configuration to
 * override defaults.
 */
public class VertxContextWorkQueueConfiguration implements WorkQueueConfiguration {
    private JsonObject config;

    public VertxContextWorkQueueConfiguration(JsonObject config) {
        this.config = config;
    }

    @Override
    public int getWorkQueueSize() {
        return config.getInteger("workQueueSize", 20);
    }

    @Override
    public int getWorkQueueBroadcastInterval() {
        return 30;
    }
}
