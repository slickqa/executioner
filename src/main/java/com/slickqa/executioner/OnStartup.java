package com.slickqa.executioner;

/**
 * This interface is for a class that wants a method to be called on startup.
 * It should be annotated with the AutoloadComponent annotation for slick to
 * auto load it.
 */
@CollectableComponentType
public interface OnStartup {
    void onStartup();
}
