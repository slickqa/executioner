package com.slickqa.executioner.executable;

import com.slickqa.executioner.web.ExecutionerWebVerticle;
import com.slickqa.executioner.workqueue.ExecutionerWorkQueueVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This verticle deploys all the others.
 */
public class DeployVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        Logger log = LoggerFactory.getLogger(DeployVerticle.class);
        log.info("Starting Work Queue.");
        vertx.deployVerticle(new ExecutionerWorkQueueVerticle(), onFinished -> {
            if(onFinished.succeeded()) {
                vertx.deployVerticle(new ExecutionerWebVerticle(), result -> {
                    startFuture.succeeded();
                });
            } else {
                log.error("Starting WorkQueue Failed: ", onFinished.cause());
                startFuture.fail(onFinished.cause());
            }
        });
    }
}
