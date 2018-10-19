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
        field.setPrefWidth(
                UIUtils.computeTextWidth(field.getFont(), field.getText()) + SIZE_BUFFER);
    }
}
