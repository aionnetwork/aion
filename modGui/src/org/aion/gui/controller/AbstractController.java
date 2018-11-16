/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.gui.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.RefreshEvent;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public abstract class AbstractController implements Initializable {
    protected static final String ERROR_STYLE = "error-label";
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    @FXML private Node parent;

    private static final ExecutorService API_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public final void initialize(final URL location, final ResourceBundle resources) {
        registerEventBusConsumer();
        internalInit(location, resources);
    }

    protected void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).register(this);
    }

    @Subscribe
    private void handleRefreshEvent(final RefreshEvent event) {
        refreshView(event);
    }

    protected final <T, R> Task<R> getApiTask(final Function<T, R> consumer, T param) {
        return new Task<>() {
            @Override
            protected R call() {
                return consumer.apply(param);
            }
        };
    }

    protected final <T> void runApiTask(
            final Task<T> executeAppTask,
            final EventHandler<WorkerStateEvent> successHandler,
            final EventHandler<WorkerStateEvent> errorHandler,
            final EventHandler<WorkerStateEvent> cancelledHandler) {
        executeAppTask.setOnSucceeded(successHandler);
        executeAppTask.setOnFailed(errorHandler);
        executeAppTask.setOnCancelled(cancelledHandler);

        API_EXECUTOR.submit(executeAppTask);
    }

    protected final EventHandler<WorkerStateEvent> getEmptyEvent() {
        return event -> {};
    }

    protected final EventHandler<WorkerStateEvent> getErrorEvent(
            Consumer<Throwable> consumer, Task t) {
        return event -> {
            Throwable e = t.getException();
            if (e != null) {
                LOG.error(e.getMessage(), e);
                consumer.accept(e);
            }
        };
    }

    protected final boolean isInView() {
        return parent != null && parent.isVisible();
    }

    protected void refreshView(final RefreshEvent event) {}

    protected abstract void internalInit(final URL location, final ResourceBundle resources);

    @VisibleForTesting
    ExecutorService getExecutor() {
        return API_EXECUTOR;
    }
}
