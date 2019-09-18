package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

//TODO: [unity] rename and revise the test cases
public class BlockHeaderSealtypeTest {

    @Mock A0BlockHeader mockMiningHeader;
    @Mock StakingBlockHeader mockStakingHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSupportedVersion() {
        when(mockMiningHeader.getSealType()).thenReturn(BlockSealType.SEAL_POW_BLOCK);
        when(mockStakingHeader.getSealType()).thenReturn(BlockSealType.SEAL_POS_BLOCK);

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
        when(mockMiningHeader.getSealType()).thenReturn(BlockSealType.SEAL_NA);
        when(mockStakingHeader.getSealType()).thenReturn(BlockSealType.SEAL_NA);

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
