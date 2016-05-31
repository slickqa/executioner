package com.slickqa.executioner.workqueue;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;

/**
 * Starts Executioner
 */
public class ExecutionerWorkQueueVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    WorkQueueConfiguration config;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(ExecutionerWorkQueueVerticle.class);

        logger.debug("Configuring Guice Injector for work queue.");
        Injector injector = Guice.createInjector(new WorkQueueGuiceModule(vertx));
        injector.injectMembers(this);

        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        logger.info("Executioner WorkQueue initilized.");
    }
}
