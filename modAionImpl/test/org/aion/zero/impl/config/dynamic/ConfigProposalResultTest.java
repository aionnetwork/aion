package org.aion.zero.impl.config.dynamic;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class ConfigProposalResultTest {
    @Test
    public void testCtorAndGetter() {
        boolean success = true;
        Throwable cause = new IllegalMonitorStateException();
        ConfigProposalResult unit = new ConfigProposalResult(success, cause);
        assertThat(unit.getErrorCause(), is(cause));
        assertThat(unit.isSuccess(), is(success));
    }

    @Test
    public void testToString() {
        boolean success = true;
        Throwable cause = new IllegalMonitorStateException();
        ConfigProposalResult unit = new ConfigProposalResult(success, cause);
        assertThat(
                unit.toString(),
                is(
                        "ConfigProposalResult{success=true, errorCause=java.lang.IllegalMonitorStateException}"));
    }
}
