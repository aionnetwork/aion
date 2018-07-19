package org.aion.wallet.events;


import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.util.Duration;

public class IdleMonitor {
    private final Timeline idleTimeline;

    private final EventHandler<Event> userEventHandler;

    public IdleMonitor(Duration idleTime, Runnable notifier) {
        idleTimeline = new Timeline(new KeyFrame(idleTime, e -> notifier.run()));
        idleTimeline.setCycleCount(Animation.INDEFINITE);

        userEventHandler = e -> notIdle();

        startMonitoring();
    }

    public void register(Scene scene, EventType<? extends Event> eventType) {
        scene.addEventFilter(eventType, userEventHandler);
    }

    public void unregister(Scene scene, EventType<? extends Event> eventType) {
        scene.removeEventFilter(eventType, userEventHandler);
    }

    public void startMonitoring() {
        idleTimeline.playFromStart();
    }

    public void stopMonitoring() {
        idleTimeline.stop();
    }

    private void notIdle() {
        if (idleTimeline.getStatus() == Animation.Status.RUNNING) {
            idleTimeline.playFromStart();
        }
    }
}
