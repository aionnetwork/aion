package org.aion.gui.controller;

import com.google.common.annotations.VisibleForTesting;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.aion.gui.controller.partials.AccountsController;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.model.GeneralKernelInfoRetriever;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.mcf.config.Cfg;
import org.aion.os.KernelLauncher;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.storage.WalletStorage;
import org.aion.wallet.ui.components.account.AccountCellFactory;
import org.aion.wallet.ui.components.partials.AddAccountDialog;
import org.slf4j.Logger;

import java.io.Console;
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
public class MyBuilderFactory implements BuilderFactory {
    /** maps a class to a method that constructs an instance of it */
    private final Map<Class, Builder<?>> builderChooser;
    private final BuilderFactory fallback;

    private AccountManager accountManager;
    private ConsoleManager consoleManager;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    @VisibleForTesting
    MyBuilderFactory(BuilderFactory fallback) {
        this.fallback = fallback;
        this.builderChooser = new HashMap<>() {{
            put(AccountCellFactory.class, () -> new AccountCellFactory(accountManager, consoleManager));
            put(AddAccountDialog.class, () -> new AddAccountDialog(accountManager, consoleManager));
        }};
    }

    public MyBuilderFactory() {
        this(new JavaFXBuilderFactory());
    }

    @Override
    public Builder<?> getBuilder(Class<?> clazz) {
        Builder<?> builder = builderChooser.get(clazz);
        if(null != builder) {
            System.out.println(
                    String.format("MyBuilderFactory#getBuilder(%s) with custom builder", clazz.toString()));
            return builder;
        } else {
            System.out.println(
                    String.format("MyBuilderFactory#getBuilder(%s) used fallback builder", clazz.toString()));
            return fallback.getBuilder(clazz);
        }
    }

    public MyBuilderFactory withAccountManager(AccountManager accountManager) {
        this.accountManager = accountManager;
        return this;
    }

    public AccountManager getAccountManager() {
        return this.accountManager;
    }

    public ConsoleManager getConsoleManager() {
        return this.consoleManager;
    }

    public MyBuilderFactory withConsoleManager(ConsoleManager consoleManager) {
        this.consoleManager = consoleManager;
        return this;
    }

}