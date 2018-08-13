package org.aion.gui.model;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class ApplyConfigResultTest {
    @Test
    public void testCtorAndGetters() {
        boolean success = true;
        String displayableError = "helloWorld";
        Throwable cause = new IndexOutOfBoundsException();
        ApplyConfigResult unit = new ApplyConfigResult(success, displayableError, cause);
        assertThat(unit.isSucceeded(), is(success));
        assertThat(unit.getDisplayableError(), is(displayableError));
        assertThat(unit.getCause(), is(cause));
    }

}