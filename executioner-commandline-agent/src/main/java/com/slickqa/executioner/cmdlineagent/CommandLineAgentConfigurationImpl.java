package com.slickqa.executioner.cmdlineagent;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Actual implementation of config based on JsonObject obtained (probably) from the Vertx context config.
 */
public class CommandLineAgentConfigurationImpl implements CommandLineAgentConfiguration {
    private JsonObject config;

    public CommandLineAgentConfigurationImpl(JsonObject config) {
        this.config = config;
    }

    @Override
    public String getAgentName() {
        return config.getString("name");
    }

    @Override
    public JsonArray getProvides() {
        return config.getJsonArray("provides", new JsonArray());
    }

    @Override
    public JsonObject getAgentInformation() {
        return config.getJsonObject("information", new JsonObject());
    }

    @Override
    public String getImageWatchPath() {
        return config.getString("imagePath", "/tmp/screen.png");
    }

    @Override
    public String getCommand() {
        return config.getString("command");
    }
}
