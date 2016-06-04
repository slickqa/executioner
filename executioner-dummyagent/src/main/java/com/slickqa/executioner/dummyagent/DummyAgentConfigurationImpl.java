package com.slickqa.executioner.dummyagent;

import io.vertx.core.json.JsonObject;

/**
 * Implementation of dummy agent configuration based on vertx context configuration.
 */
public class DummyAgentConfigurationImpl implements DummyAgentConfiguration {
    private JsonObject config;

    public DummyAgentConfigurationImpl(JsonObject config) {
        this.config = config;
    }

    @Override
    public int getDummyAgentNumber() {
        return config.getInteger("agentNumber", 0);
    }
}
