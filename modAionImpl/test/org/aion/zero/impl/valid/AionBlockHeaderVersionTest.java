package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.mcf.types.AbstractBlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AionBlockHeaderVersionTest {

    @Mock A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSupportedVersion() {
        when(mockHeader.getSealType()).thenReturn(AbstractBlockHeader.BlockSealType.SEAL_POW_BLOCK);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        HeaderSealTypeRule rule = new HeaderSealTypeRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testUnsupportedVersion() {
        when(mockHeader.getSealType()).thenReturn(AbstractBlockHeader.BlockSealType.SEAL_NA);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        HeaderSealTypeRule rule = new HeaderSealTypeRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isFalse();
        assertThat(errors).isNotEmpty();

        System.out.println(errors.get(0).error);
    }
}
