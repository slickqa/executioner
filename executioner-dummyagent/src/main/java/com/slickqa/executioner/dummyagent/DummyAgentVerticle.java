package com.slickqa.executioner.dummyagent;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;

/**
 * A dummy agent is used for testing executioner.  It only asks for tasks that require "dummyagent".
 */
public class DummyAgentVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    DummyAgentConfiguration config;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(DummyAgentVerticle.class);

        logger.debug("Configuring Guice Injector for dummy agent.");
        Injector injector = Guice.createInjector(new DummyAgentGuiceModule(vertx));
        injector.injectMembers(this);

        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        logger.info("Executioner Dummy Agent {0} initilized.", config.getDummyAgentNumber());
    }

}
