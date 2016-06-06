package com.slickqa.executioner.cmdlineagent;

import com.google.inject.Inject;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;

/**
 * The actual agent
 */
@AutoloadComponent
public class CommandLineAgent implements OnStartup {

    private EventBus eventBus;
    private CommandLineAgentConfiguration config;
    private Vertx vertx;
    private FileSystem fs;

    @Inject
    public CommandLineAgent(EventBus eventBus, CommandLineAgentConfiguration config, Vertx vertx, FileSystem fs) {
        this.eventBus = eventBus;
        this.config = config;
        this.vertx = vertx;
        this.fs = fs;
    }

    @Override
    public void onStartup() {

    }
}
