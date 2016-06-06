package com.slickqa.executioner.web;

import io.vertx.core.json.JsonObject;

/**
 * An implementation of the Executioner Web Configuration based on vertx config.
 */
public class ExecutionerWebConfigurationImpl  implements ExecutionerWebConfiguration {
    private JsonObject config;

    public ExecutionerWebConfigurationImpl(JsonObject config) {
        this.config = config;
    }

    @Override
    public int getWebPort() {
        return config.getInteger("executionerWebPort", 8000);
    }

    @Override
    public String getWebBasePath() {
        return config.getString("executionerWebBaseUrl", "/");
    }

    @Override
    public String getAgentImagesDir() {
        return config.getString("agentImagesDir", "agent-images/");
    }
}
