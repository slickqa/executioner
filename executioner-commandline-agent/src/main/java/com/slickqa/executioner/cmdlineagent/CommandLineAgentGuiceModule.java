package com.slickqa.executioner.cmdlineagent;

import com.slickqa.executioner.base.ExecutionerGuiceModule;
import io.vertx.core.Vertx;

/**
 * Guice module for command line agent.
 */
public class CommandLineAgentGuiceModule extends ExecutionerGuiceModule {

    public CommandLineAgentGuiceModule(Vertx vertx) {
        super(vertx, "com.slickqa.executioner.cmdlineagent");
    }

    @Override
    public void configure() {
        super.configure();
        bind(CommandLineAgentConfiguration.class).toInstance(new CommandLineAgentConfigurationImpl(vertx.getOrCreateContext().config()));
    }
}
