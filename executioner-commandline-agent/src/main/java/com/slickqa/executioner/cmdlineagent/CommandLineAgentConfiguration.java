package com.slickqa.executioner.cmdlineagent;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Interface representing configuration for command line agents
 */
public interface CommandLineAgentConfiguration {
    String getAgentName();
    JsonArray getProvides();
    JsonObject getAgentInformation();
    String getImageWatchPath();
    String getCommand();
}
