package com.slickqa.executioner.workqueue;

import com.google.inject.AbstractModule;
import com.slickqa.executioner.base.ExecutionerGuiceModule;
import io.vertx.core.Vertx;

/**
 * Guice module for work queue.
 */
public class WorkQueueGuiceModule extends ExecutionerGuiceModule {

    public WorkQueueGuiceModule(Vertx vertx) {
        super(vertx, "com.slickqa.executioner.workqueue");
    }

    @Override
    protected void configure() {
        super.configure();
        bind(WorkQueueConfiguration.class).toInstance(new VertxContextWorkQueueConfiguration(vertx.getOrCreateContext().config()));
    }
}
