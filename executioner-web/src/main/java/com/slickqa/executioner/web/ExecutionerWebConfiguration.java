package com.slickqa.executioner.web;

/**
 * Interface representing all configurable elements of the Executioner web interface.
 */
public interface ExecutionerWebConfiguration {
    int getWebPort();
    String getWebBasePath();
    String getAgentImagesDir();
}
