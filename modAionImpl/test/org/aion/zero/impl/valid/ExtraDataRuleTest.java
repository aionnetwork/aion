package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExtraDataRuleTest {

    private final BlockConstants constants = new BlockConstants();

    @Mock A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEmptyByteArray() {
        byte[] EMPTY_BYTE_ARR = new byte[0];

        when(mockHeader.getExtraData()).thenReturn(EMPTY_BYTE_ARR);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
    }

    // even though this should never happen in production
    @Test
    public void testNullByteArray() {
        when(mockHeader.getExtraData()).thenReturn(null);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testInvalidLargerByteArray() {
        byte[] LARGE_BYTE_ARRAY = new byte[33];

        when(mockHeader.getExtraData()).thenReturn(LARGE_BYTE_ARRAY);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
    }
}
