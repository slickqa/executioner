package com.slickqa.executioner.cmdlineagent;

import io.vertx.core.json.JsonArray;

/**
 * Interface representing configuration for command line agents
 */
public interface CommandLineAgentConfiguration {
    String getAgentName();
    JsonArray getProvides();
    String getImageWatchPath();
    String getCommand();
}
