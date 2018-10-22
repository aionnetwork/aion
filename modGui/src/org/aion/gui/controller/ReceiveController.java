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
import java.util.EnumSet;
import java.util.ResourceBundle;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;

public class ReceiveController implements Initializable {
    @FXML public ImageView qrCode;

    @FXML private TextArea accountAddress;

    private Tooltip copiedTooltip;

    private AccountDTO account;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        registerEventBusConsumer();
        copiedTooltip = new Tooltip();
        copiedTooltip.setText("Copied");
        copiedTooltip.setAutoHide(true);
        accountAddress.setTooltip(copiedTooltip);
    }

    private void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).register(this);
    }

    @Subscribe
    private void handleAccountChanged(final AccountEvent event) {
        if (EnumSet.of(AccountEvent.Type.CHANGED, AccountEvent.Type.ADDED)
                .contains(event.getType())) {
            account = event.getPayload();
            accountAddress.setText(account.getPublicAddress());

            Image image = SwingFXUtils.toFXImage(account.getQrCode(), null);
            qrCode.setImage(image);
        } else if (AccountEvent.Type.LOCKED.equals(event.getType())) {
            if (event.getPayload().equals(account)) {
                account = null;
                accountAddress.setText("No account selected!");
                qrCode.setImage(null);
            }
        }
    }

    @VisibleForTesting
    void setAccount(AccountDTO account) {
        this.account = account;
    }

    public void onCopyToClipBoard() {
        if (account != null && account.getPublicAddress() != null) {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(account.getPublicAddress());
            clipboard.setContent(content);

            copiedTooltip.show(accountAddress.getScene().getWindow());
        }
    }
}
