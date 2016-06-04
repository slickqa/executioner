package com.slickqa.executioner.web;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Set;

/**
 * The web interface for monitoring and interacting with executioner.
 */
public class ExecutionerWebVerticle extends AbstractVerticle {

    @Inject
    private Set<OnStartup> startupSet;

    @Inject
    private Router router;

    @Inject
    ExecutionerWebConfiguration config;

    @Inject
    FileSystem fs;

    @Override
    public void start() throws Exception {
        Logger logger = LoggerFactory.getLogger(ExecutionerWebVerticle.class);

        logger.debug("Configuring Guice Injector");
        Injector injector = Guice.createInjector(new ExecutionerWebGuiceModule(vertx));
        injector.injectMembers(this);

        BodyHandler bodyHandler = BodyHandler.create();
        router.route(HttpMethod.POST, config.getWebBasePath() + "api/*").handler(bodyHandler);
        router.route(HttpMethod.PUT, config.getWebBasePath() + "api/*").handler(bodyHandler);

        if(!fs.existsBlocking("agent-images")) {
            fs.mkdirBlocking("agent-images");
        }


        for(OnStartup startupComponent: startupSet) {
            startupComponent.onStartup();
        }

        router.route(config.getWebBasePath() + "agent-images/*").handler(StaticHandler.create().setWebRoot("agent-images"));
        router.route(config.getWebBasePath() + "*").handler(StaticHandler.create());

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config.getWebPort());

        logger.info("Executioner Web initilized, listening on port {0}.", config.getWebPort());
    }

}
