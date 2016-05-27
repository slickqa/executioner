package com.slickqa.executioner;

import io.vertx.core.json.Json;
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

    public String getUrlBasePath() {
        return config.getString("urlBasePath", "/");
    }
}
