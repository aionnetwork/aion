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

package org.aion.gui.views;

import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * Encapsulates logic of using RichTextFX's CodeArea. Because {@link VirtualizedScrollPane} doesn't
 * play nice with FXML, we will take care of all that set up in this class, which can be used with
 * FXML properly.
 */
public class XmlArea extends StackPane {
    private CodeArea codeArea;

    public XmlArea() {
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        VirtualizedScrollPane vsp = new VirtualizedScrollPane<>(codeArea);
        codeArea.setStyle("-fx-font-family: monospace, DejaVu Sans Mono; -font-size: 10px;");
        getChildren().add(vsp);
    }

    public void setText(String text) {
        codeArea.replaceText(text);
        codeArea.scrollToPixel(0, 0);
    }

    public String getText() {
        return codeArea.getText();
    }
}
