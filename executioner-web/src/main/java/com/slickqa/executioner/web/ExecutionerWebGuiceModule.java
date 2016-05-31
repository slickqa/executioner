package com.slickqa.executioner.web;

import com.google.inject.AbstractModule;
import com.slickqa.executioner.base.ExecutionerGuiceModule;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

/**
 * Guice Module for Executioner Web
 */
public class ExecutionerWebGuiceModule extends ExecutionerGuiceModule {

    public ExecutionerWebGuiceModule(Vertx vertx) {
        super(vertx, "com.slickqa.executioner.web");
    }


    @Override
    protected void configure() {
        super.configure();
        bind(Router.class).toInstance(Router.router(vertx));
        bind(ExecutionerWebConfiguration.class).toInstance(new ExecutionerWebConfigurationImpl(vertx.getOrCreateContext().config()));
    }
}
