package com.slickqa.executioner.slickv4connector;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;

/**
 * The Vert.x Verticle that starts the slickv4 connection.
 */
public class Slickv4ConnectorVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    private Slickv4Configuration config;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(Slickv4ConnectorVerticle.class);

        logger.debug("Configuring Guice Injector for dummy agent.");
        Injector injector = Guice.createInjector(new Slickv4GuiceModule(vertx));
        injector.injectMembers(this);

        logger.info("Looking for scheduled results for project {0} on slick at {1}",
                    config.getProjectName(), config.getSlickUrl());

        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        logger.info("Executioner Slickv4Connector Initialized.");

    }

}
