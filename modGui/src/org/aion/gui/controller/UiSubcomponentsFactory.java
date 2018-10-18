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
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.ui.components.account.AccountCellFactory;
import org.aion.wallet.ui.components.partials.AddAccountDialog;
import org.slf4j.Logger;

/**
 * {@link BuilderFactory} that builds sub-components used in controller layer.
 */
public class UiSubcomponentsFactory implements BuilderFactory {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());
    /**
     * maps a class to a method that constructs an instance of it
     */
    private final Map<Class, Builder<?>> builderChooser;
    private final BuilderFactory fallback;
    private AccountManager accountManager;
    private ConsoleManager consoleManager;

    @VisibleForTesting
    UiSubcomponentsFactory(BuilderFactory fallback) {
        this.fallback = fallback;
        this.builderChooser = new HashMap<>() {{
            put(AccountCellFactory.class,
                () -> new AccountCellFactory(accountManager, consoleManager));
            put(AddAccountDialog.class, () -> new AddAccountDialog(accountManager, consoleManager));
        }};
    }

    public UiSubcomponentsFactory() {
        this(new JavaFXBuilderFactory());
    }

    @Override
    public Builder<?> getBuilder(Class<?> clazz) {
        Builder<?> builder = builderChooser.get(clazz);
        if (null != builder) {
            return builder;
        } else {
            return fallback.getBuilder(clazz);
        }
    }

    public UiSubcomponentsFactory withAccountManager(AccountManager accountManager) {
        this.accountManager = accountManager;
        return this;
    }

    public AccountManager getAccountManager() {
        return this.accountManager;
    }

    public ConsoleManager getConsoleManager() {
        return this.consoleManager;
    }

    public UiSubcomponentsFactory withConsoleManager(ConsoleManager consoleManager) {
        this.consoleManager = consoleManager;
        return this;
    }
}