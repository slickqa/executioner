package com.slickqa.executioner.base;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.shareddata.SharedData;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Configure Guice injection for slick.  Reads Named String values from Vert.x
 *
 */
public class ExecutionerGuiceModule extends AbstractModule {
    protected final Vertx vertx;
    private final Reflections reflections;

    public ExecutionerGuiceModule(Vertx vertx, String basePackage) {
        this.vertx = vertx;
        reflections = new Reflections(basePackage);
    }

    @Override
    protected void configure() {
        bind(Vertx.class).toInstance(vertx);
        bind(EventBus.class).toInstance(vertx.eventBus());
        bind(SharedData.class).toInstance(vertx.sharedData());
        bind(FileSystem.class).toInstance(vertx.fileSystem());
        bind(HttpClient.class).toInstance(vertx.createHttpClient());
        Set<Class> collectables = new HashSet<Class>();
        for(Class cls : reflections.getTypesAnnotatedWith(CollectableComponentType.class)) {
            if(cls.isInterface()) {
                collectables.add(cls);
            }
        }
        collectables.add(OnStartup.class);
        addBindingsFor(collectables);
    }

    protected void addBindingsFor(Set<Class> collectables) {
        Map<Class, Multibinder> binders = new HashMap<>();
        for(Class collectable : collectables) {
            binders.put(collectable, Multibinder.newSetBinder(binder(), collectable));
        }
        for(Class component : reflections.getTypesAnnotatedWith(AutoloadComponent.class)) {
            for(Class collectable : collectables) {
                if(collectable.isAssignableFrom(component) &&
                   !component.isInterface() &&
                   !Modifier.isAbstract(component.getModifiers())) {
                    binders.get(collectable).addBinding().to(component);
                }
            }
        }
    }
}
