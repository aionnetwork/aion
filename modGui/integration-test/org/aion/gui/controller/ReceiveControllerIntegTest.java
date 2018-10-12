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

import static javafx.fxml.FXMLLoader.DEFAULT_CHARSET_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.util.LinkedList;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.aion.gui.events.EventBusRegistry;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

public class ReceiveControllerIntegTest extends ApplicationTest {

    private ReceiveController controller;
    private ControllerFactory cf;
    private Parent receivePaneView;
    private EventBusRegistry ebr;

    @Override
    public void init() {

    }

    @Override
    public void start(Stage stage) throws Exception {
        ebr = new EventBusRegistry();

        cf = new ControllerFactory()
            .withEventBusRegistry(ebr);
        FXMLLoader loader = new FXMLLoader(
            ReceiveController.class.getResource("components/partials/ReceivePane.fxml"),
            null,
            null,
            cf,
            Charset.forName(DEFAULT_CHARSET_NAME),
            new LinkedList<>());
        receivePaneView = loader.load();
        controller = loader.getController();

        AnchorPane ap = new AnchorPane(receivePaneView);
        ap.setPrefWidth(860);
        ap.setPrefHeight(570);

        stage.setScene(new Scene(ap));
        stage.show();
        stage.toFront();

        lookup("#receivePane").query().setVisible(true);

    }

    @Test
    public void testInitial() {
        assertThat(((ImageView) receivePaneView.lookup("#qrCode")).getImage(), is(nullValue()));

        AccountDTO currentAccount = mock(AccountDTO.class);
        controller.setAccount(currentAccount);
        clickOn(
            "#copyToClipboardButton"); // nothing to really verify, just click it to make sure no exceptions
    }

    @Test
    public void testInitialThenChangeToSomeAccount() throws Exception {
        String testAddress = "some test address";
        AccountDTO account = new AccountDTO(
            "anyName", testAddress, "anyBalance", "anyCurrency", false, 0
        );
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID)
            .post(new AccountEvent(AccountEvent.Type.CHANGED, account));

        // ReceiveController#handleAccountChanged deep copies the qrCode image so the
        // only way to verify it is to convert it back into a format that QrReader understands...
        Image img = ((ImageView) receivePaneView.lookup("#qrCode")).getImage();
        BufferedImage image = SwingFXUtils.fromFXImage(img, null);
        int[] rgb = image
            .getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        RGBLuminanceSource rgbSource = new RGBLuminanceSource(image.getWidth(), image.getHeight(),
            rgb);
        Result qr = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(rgbSource)));
        assertThat(qr.getText(), is(testAddress));

        assertThat(
            ((TextArea) receivePaneView.lookup("#accountAddress")).getText(),
            is(account.getPublicAddress()));

        clickOn("#copyToClipboardButton");
        Platform.runLater(
            () -> assertThat(Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT),
                is(account.getPublicAddress())));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testSomeAccountThenChangeToOtherAccount() throws Exception {
        AccountDTO account = new AccountDTO(
            "anyName", "anyAddr", "anyBalance", "anyCurrency", false, 0
        );
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID)
            .post(new AccountEvent(AccountEvent.Type.CHANGED, account));

        String otherAddress = "some other address";
        AccountDTO otherAccount = new AccountDTO(
            "otherName", otherAddress, "otherBalance", "otherCurrency", false, 1
        );
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID)
            .post(new AccountEvent(AccountEvent.Type.CHANGED, otherAccount));

        // ReceiveController#handleAccountChanged deep copies the qrCode image so the
        // only way to verify it is to convert it back into a format that QrReader understands...
        Image img = ((ImageView) receivePaneView.lookup("#qrCode")).getImage();
        BufferedImage image = SwingFXUtils.fromFXImage(img, null);
        int[] rgb = image
            .getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        RGBLuminanceSource rgbSource = new RGBLuminanceSource(image.getWidth(), image.getHeight(),
            rgb);
        Result qr = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(rgbSource)));
        assertThat(qr.getText(), is(otherAddress));

        assertThat(
            ((TextArea) receivePaneView.lookup("#accountAddress")).getText(),
            is(otherAccount.getPublicAddress()));

        clickOn("#copyToClipboardButton");
        Platform.runLater(
            () -> assertThat(Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT),
                is(otherAccount.getPublicAddress())));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testSomeAccountThenLockAccount() {
        AccountDTO account = new AccountDTO(
            "anyName", "anyAddr", "anyBalance", "anyCurrency", false, 0
        );
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID)
            .post(new AccountEvent(AccountEvent.Type.CHANGED, account));

        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID)
            .post(new AccountEvent(AccountEvent.Type.LOCKED, account));
        assertThat(((ImageView) receivePaneView.lookup("#qrCode")).getImage(), is(nullValue()));
        assertThat(
            ((TextArea) receivePaneView.lookup("#accountAddress")).getText(),
            is("No account selected!"));
    }
}