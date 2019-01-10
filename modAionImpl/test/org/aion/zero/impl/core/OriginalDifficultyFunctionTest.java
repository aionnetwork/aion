package org.aion.zero.impl.core;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.zero.api.BlockConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public class OriginalDifficultyFunctionTest {

    protected static class InputParameters {
        public final BigInteger parentDifficulty;
        public final long timeDelta;

        public InputParameters(BigInteger parentDifficulty, long timeDelta) {
            this.parentDifficulty = parentDifficulty;
            this.timeDelta = timeDelta;
        }
    }

    @Mock protected AbstractBlockHeader mockHeader;

    @Mock protected AbstractBlockHeader parentMockHeader;

    @Parameter public InputParameters inputParameters;

    @Parameter(1)
    public BigInteger expectedDifficulty;

    @Parameters
    public static Collection<Object[]> data() {
        BlockConstants constants = new BlockConstants();

        return Arrays.asList(
                new Object[][] {
                    {new InputParameters(new BigInteger("1000000"), 0), new BigInteger("1000488")},
                    {new InputParameters(new BigInteger("1000000"), 10), new BigInteger("999512")},
                    // try a arbitrary high value to verify that the result stays consistent
                    {
                        new InputParameters(new BigInteger("1000000"), 1000000),
                        new BigInteger("999512")
                    },
                    // check that difficulty has a lower bound
                    {new InputParameters(new BigInteger("0"), 0), constants.getMinimumDifficulty()},
                });
    }

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() {
        DiffCalc dc = new DiffCalc(new BlockConstants());

        when(mockHeader.getTimestamp()).thenReturn(inputParameters.timeDelta);
        when(parentMockHeader.getTimestamp()).thenReturn(0L);
        when(parentMockHeader.getDifficulty())
                .thenReturn(inputParameters.parentDifficulty.toByteArray());
        when(parentMockHeader.getDifficultyBI()).thenReturn(inputParameters.parentDifficulty);

        BigInteger difficulty = dc.calcDifficulty(mockHeader, parentMockHeader);
        assertThat(difficulty).isEqualTo(expectedDifficulty);
    }
}
