package org.aion.gui.views;

import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * Encapsulates logic of using RichTextFX's CodeArea.  Because {@link VirtualizedScrollPane} doesn't
 * play nice with FXML, we will take care of all that set up in this class, which can be used with
 * FXML properly.
 */
public class XmlArea extends StackPane {
    private CodeArea codeArea;

    public XmlArea() {
        System.out.println("XML AREA BEING CONSTRUCTED.");
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
