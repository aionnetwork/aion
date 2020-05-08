package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader.Seal;
import org.aion.zero.impl.types.MiningBlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BlockHeaderSealtypeTest {

    @Mock
    MiningBlockHeader mockMiningHeader;
    @Mock StakingBlockHeader mockStakingHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSupportedVersion() {
        when(mockMiningHeader.getSealType()).thenReturn(Seal.PROOF_OF_WORK);
        when(mockStakingHeader.getSealType()).thenReturn(Seal.PROOF_OF_STAKE);

        List<RuleError> errors = new ArrayList<>();

        HeaderSealTypeRule rule = new HeaderSealTypeRule();
        boolean result = rule.validate(mockMiningHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();

        result = rule.validate(mockStakingHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();

    }

    @Test
    public void testUnsupportedVersion() {
        when(mockMiningHeader.getSealType()).thenReturn(Seal.NOT_APPLICABLE);
        when(mockStakingHeader.getSealType()).thenReturn(Seal.NOT_APPLICABLE);

        List<RuleError> errors = new ArrayList<>();

        HeaderSealTypeRule rule = new HeaderSealTypeRule();
        boolean result = rule.validate(mockMiningHeader, errors);

        assertThat(result).isFalse();
        assertThat(errors).isNotEmpty();
        System.out.println(errors.get(0).error);

        result = rule.validate(mockStakingHeader, errors);

        assertThat(result).isFalse();
        assertThat(errors).isNotEmpty();
        System.out.println(errors.get(0).error);

    }
}
