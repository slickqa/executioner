package com.slickqa.executioner;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Set;

/**
 * Starts Executioner
 */
public class ExecutionerVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    Configuration config;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(ExecutionerVerticle.class);

        logger.debug("Configuring Guice Injector");
        Injector injector = Guice.createInjector(new ExecutionerGuiceModule(vertx));
        injector.injectMembers(this);

        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        logger.info("Executioner initilized.");
    }
}
