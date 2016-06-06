package com.slickqa.executioner.cmdlineagent;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;

/**
 * The command line agent, which based on json configuration runs a command for it's task.
 */
public class CommandLineAgentVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    CommandLineAgentConfiguration config;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(CommandLineAgentVerticle.class);

        logger.debug("Configuring Guice Injector for command line agent.");
        Injector injector = Guice.createInjector(new CommandLineAgentGuiceModule(vertx));
        injector.injectMembers(this);

        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        logger.info("Started command line agent: {0}", config.getAgentName());
    }

}
