package org.aion.gui.events;

/** An event related to the OS process running the kernel */
public class KernelProcEvent {
    public static class KernelLaunchedEvent extends KernelProcEvent {}
    public static class KernelTerminatedEvent extends KernelProcEvent {}
}
