package com.slickqa.executioner.slickv4connector;

import io.vertx.core.json.JsonObject;

/**
 * Actual implementation of configuration based on Json object configuration.
 */
public class Slickv4ConfigurationImpl implements Slickv4Configuration {
    protected JsonObject config;

    public Slickv4ConfigurationImpl(JsonObject config) {
        this.config = config;
    }

    @Override
    public String getSlickUrl() {
        return config.getString("slickUrl");
    }

    @Override
    public String getProjectName() {
        return config.getString("project");
    }

    @Override
    public String getExecutionerAgentName() {
        return config.getString("agentName", "unknown");
    }

    @Override
    public int getPollingInterval() {
        return config.getInteger("pollEvery", 10);
    }

    @Override
    public int getQueueSizeLowerBound() {
        return config.getInteger("pollWhenQueueSizeLessThan", 1000);
    }

    @Override
    public int getSimultaneousFetchLimit() {
        return config.getInteger("slickFetchLimit", 50);
    }
}
