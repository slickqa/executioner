package com.slickqa.executioner.base;

/**
 * Addresses on the event bus used by various parts of executioner
 */
public class Addresses {
    public static final String WorkStop = "executioner.workqueue.stop";
    public static final String WorkStart = "executioner.workqueue.start";
    public static final String WorkQueueInfo = "executioner.workqueue.info";
    public static final String WorkQueueAdd = "executioner.workqueue.add";
    public static final String WorkQueueQuery = "executioner.workqueue.query";
    public static final String WorkQueueRequestWork = "executioner.workqueue.requestAssignment";
    public static final String WorkQueueState = "executioner.workqueue.state";
    public static final String AgentQuery = "executioner.agent.queryall";
    public static final String AgentUpdate = "executioner.agent.update";
    public static final String AgentDeleteAnnounce = "executioner.agent.delete";
    public static final String AgentStopBaseAddress = "executioner.agent.stop.";
    public static final String AgentPauseBaseAddress = "executioner.agent.pause.";
    public static final String AgentResumeBaseAddress = "executioner.agent.resume.";
    public static final String AgentBaseAddress = "executioner.agent.";
    public static final String AgentImageBaseAddress = "executioner.agent.image.";
    public static final String ExternalRequest = "executioner.external-request";
}
