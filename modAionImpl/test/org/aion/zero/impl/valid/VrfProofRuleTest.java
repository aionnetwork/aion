package org.aion.zero.impl.valid;

import com.google.common.truth.Truth;
import java.util.LinkedList;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.vrf.VRF_Ed25519;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class VrfProofRuleTest {
    byte[] defaultSeed = StakingBlockHeader.GENESIS_SEED;
    byte[] defaultProof = StakingBlockHeader.DEFAULT_PROOF;
    ECKey key;

    @Mock StakingBlockHeader mockHeader;
    @Mock StakingBlockHeader mockParentHeader;
    @Mock StakingBlockHeader mockGrandParentHeader;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        key = ECKeyFac.inst().create();
    }

    @Test
    public void testVrfProofWithIncorrectProofLength() {
        Mockito.when(mockHeader.getSeedOrProof()).thenReturn(defaultSeed);

        List<RuleError> errors = new LinkedList<>();
        boolean actual = new VRFProofRule().validate(mockParentHeader, mockGrandParentHeader, mockHeader, errors);
        Truth.assertThat(actual).isFalse();
    }

    @Test
    public void testVrfProofAtTheFirstForkBlock() {
        Mockito.when(mockGrandParentHeader.getSeedOrProof()).thenReturn(defaultSeed);
        byte[] newProof = VRF_Ed25519.generateProof(mockGrandParentHeader.getSeedOrProof(), key.getPrivKeyBytes());
        Mockito.when(mockHeader.getSeedOrProof()).thenReturn(newProof);
        Mockito.when(mockHeader.getSigningPublicKey()).thenReturn(key.getPubKey());

        List<RuleError> errors = new LinkedList<>();
        boolean actual = new VRFProofRule().validate(mockParentHeader, mockGrandParentHeader, mockHeader, errors);
        Truth.assertThat(actual).isTrue();
    }

    @Test
    public void testVrfProofAfterTheFork() {
        Mockito.when(mockGrandParentHeader.getSeedOrProof()).thenReturn(defaultProof);
        byte[] hash = VRF_Ed25519.generateProofHash(mockGrandParentHeader.getSeedOrProof());
        byte[] newProof = VRF_Ed25519.generateProof(hash, key.getPrivKeyBytes());
        Mockito.when(mockHeader.getSeedOrProof()).thenReturn(newProof);
        Mockito.when(mockHeader.getSigningPublicKey()).thenReturn(key.getPubKey());

        List<RuleError> errors = new LinkedList<>();
        boolean actual = new VRFProofRule().validate(mockParentHeader, mockGrandParentHeader, mockHeader, errors);
        Truth.assertThat(actual).isTrue();
    }
}
