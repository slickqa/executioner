package com.slickqa.executioner.slickv4connector;

import com.slickqa.executioner.base.ExecutionerGuiceModule;
import io.vertx.core.Vertx;

/**
 * Guice module that configures the Slickv4 Connector Injections
 */
public class Slickv4GuiceModule extends ExecutionerGuiceModule {

    public Slickv4GuiceModule(Vertx vertx) {
        super(vertx, "com.slickqa.executioner.slickv4connector");
    }

    @Override
    public void configure() {
        super.configure();
        bind(Slickv4Configuration.class).toInstance(new Slickv4ConfigurationImpl(vertx.getOrCreateContext().config()));
    }
}
