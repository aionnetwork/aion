package org.aion.base.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class SizeParseTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {new Bundle("10kB", true, 10L * 1024L)},
                {new Bundle("10KB", true, 10L * 1024L)},
                {new Bundle("10K", true, 10L * 1024L)},
                {new Bundle("10mB", true, 10L * 1048576L)},
                {new Bundle("10MB", true, 10L * 1048576L)},
                {new Bundle("10M", true, 10L * 1048576L)},
                {new Bundle("10gB", true, 10L * 1073741824L)},
                {new Bundle("10GB", true, 10L * 1073741824L)},
                {new Bundle("10G", true, 10L * 1073741824L)},

                // fail cases
                {new Bundle("10b", false, 0)},
                {new Bundle("10k", false, 0)},
                {new Bundle("10m", false, 0)},
                {new Bundle("10g", false, 0)}
        });
    }

    private static class Bundle {
        public final String input;
        public final boolean expectedResult;
        public final long expectedSize;

        public Bundle(String input, boolean expectedResult, long expectedSize) {
            this.input = input;
            this.expectedResult = expectedResult;
            this.expectedSize = expectedSize;
        }
    }

    private Bundle bundle;

    public SizeParseTest(Bundle bundle) {
        this.bundle = bundle;
    }

    @Test
    public void test() {
        Optional<Long> maybeSize = Utils.parseSize(bundle.input);
        assertThat(maybeSize.isPresent()).isEqualTo(bundle.expectedResult);

        maybeSize.ifPresent((p) ->
            assertThat(p).isEqualTo(bundle.expectedSize));
    }
}
