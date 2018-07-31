package org.aion.gui.controller;

import javafx.util.Callback;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.os.KernelLauncher;
import org.aion.os.UnixKernelProcessHealthChecker;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for constructing controller objects of a given {@link Class}.  All
 * controller objects for the GUI will be instantiated through this class, so
 * it kind of resembles an injector from Guice or Spring.  If this starts
 * getting unmanageable, might want to look into using a DI framework like Guice.
 *
 * Class implements {@link Callback} so it may be used by
 * {@link javafx.fxml.FXMLLoader#setControllerFactory(Callback)}.
 */
public class ControllerFactory implements Callback<Class<?>, Object> {
    /** maps a class to a method that constructs an instance of it */
    private final Map<Class, BuildMethod> builderChooser;

    private KernelConnection kernelConnection;
    private KernelLauncher kernelLauncher;
    private KernelUpdateTimer kernelUpdateTimer;
    private GeneralKernelInfoRetriever generalKernelInfoRetriever;
    private SyncInfoDto syncInfoDto;
    private UnixKernelProcessHealthChecker healthChecker;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    @FunctionalInterface
    protected interface BuildMethod {
        AbstractController build();
    }

    /**
     * Constructor.  See "withXXX" methods for setting factory parameters, i.e.
     * {@link #withKernelConnection(KernelConnection)}
     */
    public ControllerFactory() {
        this.builderChooser = new HashMap<>() {{
            put(DashboardController.class, () -> new DashboardController(
                    kernelLauncher,
                    kernelConnection,
                    kernelUpdateTimer,
                    generalKernelInfoRetriever,
                    healthChecker,
                    syncInfoDto
            ));
            /*put(SettingsController.class, () -> new SettingsController(
                    kernelConnection));
            put(ConnectivityStatusController.class, () -> new ConnectivityStatusController(
                    kernelConnection));
            put(PeerCountController.class, () -> new PeerCountController(
                    kernelConnection
            ));
            put(SyncStatusController.class, () -> new SyncStatusController(
                    kernelConnection
            ));*/
        }};
    }

    /**
     * {@inheritDoc}
     *
     * @param clazz the class to build
     * @return an instance of clazz
     */
    @Override
    public Object call(Class<?> clazz) {
        BuildMethod builder = builderChooser.get(clazz);
        if(null != builder) {
            LOG.debug("Instantiating {} with predefined build method", clazz.toString());
            return builder.build();
        } else {
            LOG.debug("Instantiating {} with default constructor", clazz.toString());

            // if we did not configure this class in builderChooser, fall back to try to calling
            // the class's zero-argument constructor.  if that doesn't work, give up and throw.
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException
                    | IllegalArgumentException
                    | InstantiationException
                    | InvocationTargetException
                    | IllegalAccessException ex) {
                throw new IllegalArgumentException(String.format(
                        "Error trying to construct Controller class '%s'.  It was not configured " +
                                "with a constructor call and we could not call its default constructor",
                                clazz.toString()), ex);
            }
        }
    }

    public BuildMethod getBuildMethod(Class<?> clazz) {
        return builderChooser.get(clazz);
    }

    /**
     * @param kernelConnection sets the kernel connection used by this factory
     * @return this
     */
    public ControllerFactory withKernelConnection(KernelConnection kernelConnection) {
        this.kernelConnection = kernelConnection;
        return this;
    }

    /**
     * @return the kernel connection used by this factory
     */
    public KernelConnection getKernelConnection() {
        return kernelConnection;
    }

    /**
     * @param kernelLauncher sets the kernel connection used by this factory
     * @return this
     */
    public ControllerFactory withKernelLauncher(KernelLauncher kernelLauncher) {
        this.kernelLauncher = kernelLauncher;
        return this;
    }

    /**
     * @return the kernel launcher used by this factory
     */
    public KernelLauncher getKernelLauncher() {
        return kernelLauncher;
    }

    /**
     * @param kernelUpdateTimer sets the timer used by this factory
     * @return this
     */
    public ControllerFactory withTimer(KernelUpdateTimer kernelUpdateTimer) {
        this.kernelUpdateTimer = kernelUpdateTimer;
        return this;
    }

    /**
     * @return the timer used by this factory
     */
    public KernelUpdateTimer getTimer() {
        return kernelUpdateTimer;
    }

    /**
     * @param generalKernelInfoRetriever sets the generalKernelInfoRetriever used by this factory
     * @return this
     */
    public ControllerFactory withGeneralKernelInfoRetriever(GeneralKernelInfoRetriever generalKernelInfoRetriever) {
        this.generalKernelInfoRetriever = generalKernelInfoRetriever;
        return this;
    }

    /**
     * @return the generalKernelInfoRetriever used by this factory
     */
    public GeneralKernelInfoRetriever getGeneralKernelInfoRetriever() {
        return generalKernelInfoRetriever;
    }

    /**
     * @param syncInfoDto sets the SyncInfoDto used by this factory
     * @return this
     */
    public ControllerFactory withSyncInfoDto(SyncInfoDto syncInfoDto) {
        this.syncInfoDto = syncInfoDto;
        return this;
    }

    /**
     * @return the SyncInfoDto used by this factory
     */
    public SyncInfoDto getSyncInfoDto() {
        return syncInfoDto;
    }

    public UnixKernelProcessHealthChecker getHealthChecker() {
        return this.healthChecker;
    }

    public ControllerFactory withHealthChecker(UnixKernelProcessHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
        return this;
    }
}