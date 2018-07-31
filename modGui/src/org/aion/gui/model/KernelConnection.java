package org.aion.gui.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.aion.api.IAionAPI;
import org.aion.api.impl.AionAPIImpl;
import org.aion.api.type.ApiMsg;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.events.UnexpectedApiDisconnectedEvent;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.config.CfgApi;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Represents a connection to the kernel; provides interface to connect/disconnect to kernel API
 * and retricted access to make API calls (see {@link #getApi()} and {@link AbstractAionApiClient}
 * for details).
 */
public class KernelConnection {
    private final ExecutorService backgroundExecutor;
    private final CfgApi cfgApi;
    private final IAionAPI api;
    private final EventBus eventBus;

    private Future<?> connectionFuture;
    private Future<?> disconnectionFuture;

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param cfgApi Configuration
     * @param eventBus Event bus to which notifications about connection state changes are sent
     */
    public KernelConnection(CfgApi cfgApi,
                            EventBus eventBus) {
        this(AionAPIImpl.inst(), cfgApi, eventBus, Executors.newSingleThreadExecutor());
    }


    /**
     * Constructor with injectable parameters for testing
     *
     * @param aionApi
     * @param cfgApi
     * @param eventBus
     * @param executorService
     */
    @VisibleForTesting KernelConnection(IAionAPI aionApi,
                                        CfgApi cfgApi,
                                        EventBus eventBus,
                                        ExecutorService executorService) {
        this.api = aionApi;
        this.cfgApi = cfgApi;
        this.eventBus = eventBus;
        this.backgroundExecutor = executorService;
    }

    /** Connect to API */
    public void connect() {
        if (connectionFuture != null) {
            connectionFuture.cancel(true);
        }
        connectionFuture = backgroundExecutor.submit(() -> {
            synchronized (api) {
                LOG.trace("About to connect to API");
                ApiMsg msg =  api.connect(getConnectionString(), true);
                if(msg.isError()) {
                    // since api.connect called with reconnect = true, it should
                    // block until msg is not error so this shouldn't happen, but
                    // log if it does.
                    LOG.error("Error connecting to Api.  ErrorCode = {}.  ErrString = {}",
                            msg.getErrorCode(), msg.getErrString());
                } else {
                    eventBus.post(new RefreshEvent(RefreshEvent.Type.OPERATION_FINISHED));
                }
            }
        });
    }

    /** Disconnect from API. */
    public void disconnect() {
        if (!isConnected()) {
            return;
        }
        if(connectionFuture != null) {
            connectionFuture.cancel(true);
        }
        if(disconnectionFuture != null) {
            disconnectionFuture.cancel(true);
        }
        disconnectionFuture = backgroundExecutor.submit(() -> {
            synchronized (api) {
                LOG.trace("About to destroy API");
                api.destroyApi().getObject();
            }
        });

    }

    /**
     * @return whether API is connected
     */
    public boolean isConnected() {
        synchronized (api) {
            return api.isConnected();
        }
    }

    /**
     * Intended to only be used by AbstractAionApiClient.
     *
     * @return api that the kernel is connected to
     */
    IAionAPI getApi() {
        // Impl note: Can make this public if there's a good reason for other classes
        // to call this in the future.  Because of the non-thread-safe nature of API,
        // currently it is restricted and the recommended way to call the API is to subclass
        // from AbstractAionApiClient which provides subclasses with synchronized blocks to
        // call their critical sections with.
        return this.api;
    }

    private String getConnectionString() {
        final String protocol = "tcp";
        final String ip = Preconditions.checkNotNull(cfgApi.getZmq().getIp(),
                "ip is not configured");
        final String port = Preconditions.checkNotNull(String.valueOf(cfgApi.getZmq().getPort()),
                "port is not configured");
        return protocol + "://" + ip + ":" + port;
    }
}
