package com.slickqa.executioner;

import io.vertx.core.json.JsonObject;

/**
 * Slick configuration that uses Vertx Context configuration to
 * override defaults.
 */
public class VertxContextConfiguration implements Configuration {
    private JsonObject config;

    public VertxContextConfiguration(JsonObject config) {
        this.config = config;
    }

    @Override
    public String getPathToAgentConfigs() {
        return config.getString("agents", "agents");
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
