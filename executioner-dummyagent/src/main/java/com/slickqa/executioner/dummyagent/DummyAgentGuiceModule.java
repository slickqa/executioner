package com.slickqa.executioner.dummyagent;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.slickqa.executioner.base.ExecutionerGuiceModule;
import io.vertx.core.Vertx;

/**
 * Created by jason.corbett on 6/1/16.
 */
public class DummyAgentGuiceModule extends ExecutionerGuiceModule {

    public DummyAgentGuiceModule(Vertx vertx) {
        super(vertx, "com.slickqa.executioner.dummyagent");
    }

    @Override
    protected void configure() {
        super.configure();
        bind(DummyAgentConfiguration.class).toInstance(new DummyAgentConfigurationImpl(vertx.getOrCreateContext().config()));
    }
}
