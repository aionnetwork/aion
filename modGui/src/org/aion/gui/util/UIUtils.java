package org.aion.gui.util;

import javafx.scene.control.TextField;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class UIUtils {

    private static final Text HELPER = new Text();

    private static final double DEFAULT_WRAPPING_WIDTH = HELPER.getWrappingWidth();

    private static final double DEFAULT_LINE_SPACING = HELPER.getLineSpacing();

    private static final String DEFAULT_TEXT = HELPER.getText();

    private static final int SIZE_BUFFER = 20;


    private static double computeTextWidth(final Font font, final String text) {
        HELPER.setText(text);
        HELPER.setFont(font);

        HELPER.setWrappingWidth(0.0);
        HELPER.setLineSpacing(0.0);
        double d = Math.min(HELPER.prefWidth(-1.0D), 0.0);
        HELPER.setWrappingWidth((int) Math.ceil(d));
        d = Math.ceil(HELPER.getLayoutBounds().getWidth());

        HELPER.setWrappingWidth(DEFAULT_WRAPPING_WIDTH);
        HELPER.setLineSpacing(DEFAULT_LINE_SPACING);
        HELPER.setText(DEFAULT_TEXT);
        return d;
    }

    public static void setWidth(final TextField field) {
        field.setPrefWidth(UIUtils.computeTextWidth(field.getFont(), field.getText()) + SIZE_BUFFER);
    }
}
