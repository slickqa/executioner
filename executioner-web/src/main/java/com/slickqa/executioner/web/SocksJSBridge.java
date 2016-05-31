package com.slickqa.executioner.web;

import com.google.inject.Inject;
import com.slickqa.executioner.base.AutoloadComponent;
import com.slickqa.executioner.base.OnStartup;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.util.Set;

/**
 * This class builds a SocksJS bridge for the browser.
 */
@AutoloadComponent
public class SocksJSBridge implements OnStartup {
    private Vertx vertx;
    private Router router;
    private Set<AddsSocksJSBridgeOptions> bridgeOptionComponents;
    private EventBus eb;
    private ExecutionerWebConfiguration config;

    /**
     * All socksjs registrations will get rebroadcast on the event bus
     * with this prefix and the address it's registering to.
     */
    public static final String REGISTER_EVENT_PREFIX = "socksjs.register.";

    @Inject
    public SocksJSBridge(Vertx vertx, Router router,
                         Set<AddsSocksJSBridgeOptions> bridgeOptionComponents,
                         EventBus eb, ExecutionerWebConfiguration config) {
        this.vertx = vertx;
        this.router = router;
        this.bridgeOptionComponents = bridgeOptionComponents;
        this.eb = eb;
        this.config = config;
    }

    @Override
    public void onStartup() {
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        BridgeOptions options = new BridgeOptions();
        for(AddsSocksJSBridgeOptions bridgeOptionComponent : bridgeOptionComponents) {
            bridgeOptionComponent.addToSocksJSBridgeOptions(options);
        }

        // publish on the event bus any registration from socksjs clients
        sockJSHandler.bridge(options, be -> {
            be.complete(true);
            if (be.type() == BridgeEventType.REGISTER) {
                eb.publish(REGISTER_EVENT_PREFIX + be.rawMessage().getString("address"), be.rawMessage());
            }
        });

        router.route(config.getWebBasePath() + "eventbus/*").handler(sockJSHandler);
    }
}
