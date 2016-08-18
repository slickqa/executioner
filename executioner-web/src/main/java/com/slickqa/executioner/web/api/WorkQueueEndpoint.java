package com.slickqa.executioner.web.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.slickqa.executioner.base.Addresses;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import com.slickqa.executioner.web.AddsSocksJSBridgeOptions;
import com.slickqa.executioner.web.ExecutionerWebConfiguration;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;

/**
 * This endpoint provides information on the work queue to web clients.
 */
@Singleton
@AutoloadComponent
public class WorkQueueEndpoint implements OnStartup, AddsSocksJSBridgeOptions {

    private Router router;
    private JsonArray workQueue;
    private JsonObject workQueueStatistics;
    private EventBus eventBus;
    private ExecutionerWebConfiguration config;
    private Logger log;

    @Inject
    public WorkQueueEndpoint(Router router, EventBus eventBus, ExecutionerWebConfiguration config) {
        this.log = LoggerFactory.getLogger(WorkQueueEndpoint.class);
        this.router = router;
        this.eventBus = eventBus;
        this.config = config;
        this.workQueueStatistics = new JsonObject();
    }

    @Override
    public void onStartup() {
        log.info("OnStartup called.");
        eventBus.consumer(Addresses.WorkQueueInfo).handler(this::onWorkQueueUpdated);
        eventBus.consumer(Addresses.WorkQueueStatistics, (Message<JsonObject> workStatsMessage) -> {
            workQueueStatistics = workStatsMessage.body();
        });

        eventBus.send(Addresses.WorkQueueQuery, null, result -> {
            if(result.succeeded()) {
                onWorkQueueUpdated(result.result());
            } else {
                log.warn("Query to get existing work queue failed: {0}", result.cause());
            }
        });

        router.route(HttpMethod.GET, config.getWebBasePath() + "api/workqueue").handler(this::getCurrentWorkQueue);
        router.route(HttpMethod.POST, config.getWebBasePath() + "api/workqueue").handler(this::addToWorkQueue);
        router.route(HttpMethod.GET, config.getWebBasePath() + "api/workqueue/stop").handler(this::stopWorkQueue);
        router.route(HttpMethod.GET, config.getWebBasePath() + "api/workqueue/start").handler(this::startWorkQueue);
        router.route(HttpMethod.GET, config.getWebBasePath() + "api/workqueue/stats").handler(this::getCurrentWorkQueueStatistics);
    }

    public void onWorkQueueUpdated(Message<Object> message) {
        Object body = message.body();
        if(body instanceof JsonArray) {
            workQueue = (JsonArray)body;
        } else {
            log.warn("Work Queue info was not a Json Array!!!");
            log.warn("It was of type {0}: {1}", body.getClass().getName(), body.toString());
        }
    }

    @Override
    public void addToSocksJSBridgeOptions(BridgeOptions options) {
        options.addOutboundPermitted(new PermittedOptions().setAddress(Addresses.WorkQueueInfo));
        options.addInboundPermitted(new PermittedOptions().setAddress(Addresses.WorkQueueQuery));
        options.addOutboundPermitted(new PermittedOptions().setAddress(Addresses.WorkQueueState));
        options.addOutboundPermitted(new PermittedOptions().setAddress(Addresses.WorkQueueStatistics));
        options.addInboundPermitted(new PermittedOptions().setAddress(Addresses.WorkQueueCancelItem));
    }

    public void stopWorkQueue(RoutingContext ctx) {
        eventBus.send(Addresses.WorkStop, null, message -> {
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(message.result().body()));
        });
    }

    public void startWorkQueue(RoutingContext ctx) {
        eventBus.send(Addresses.WorkStart, null, message -> {
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(message.result().body()));
        });
    }

    public void getCurrentWorkQueue(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(workQueue));
    }

    public void getCurrentWorkQueueStatistics(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(workQueueStatistics.encodePrettily());
    }

    public void addToWorkQueue(RoutingContext ctx) {
        eventBus.send(Addresses.WorkQueueAdd, ctx.getBodyAsJson(), result -> {
            if(result.succeeded()) {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encodePrettily(result.result().body()));
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encodePrettily(new JsonObject().put("error", result.cause())));
            }
        });
    }
}
