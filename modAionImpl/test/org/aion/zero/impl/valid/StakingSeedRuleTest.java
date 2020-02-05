package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StakingSeedRuleTest {

    byte[] oldSeed = new byte[64];
    ECKey key;

    @Mock StakingBlockHeader mockHeader;
    @Mock StakingBlockHeader mockParentHeader;
    @Mock StakingBlockHeader mockGrandParentHeader;

  @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Before
    public void setup() {
        key = ECKeyFac.inst().create();
    }

    @Test
    public void testValidateWithValidSeed() {
      byte[] newSeed = key.sign(oldSeed).getSignature();
      when(mockHeader.getSeed()).thenReturn(newSeed);
      when(mockHeader.getSigningPublicKey()).thenReturn(key.getPubKey());

      when(mockGrandParentHeader.getSeed()).thenReturn(oldSeed);
      List<RuleError> errors = new LinkedList<>();
      // generate output
      boolean actual = new StakingSeedRule().validate(mockGrandParentHeader, mockParentHeader, mockHeader, errors);

      // test output
      assertThat(actual).isTrue();
    }

    @Test
    public void testValidateWithInvalidSeed() {

        // now we manipulated the newseed.
        byte[] newSeed = new byte[64];
        new Random().nextBytes(newSeed);

        when(mockHeader.getSeed()).thenReturn(newSeed);
        when(mockHeader.getSigningPublicKey()).thenReturn(key.getPubKey());

        when(mockGrandParentHeader.getSeed()).thenReturn(oldSeed);
        List<RuleError> errors = new LinkedList<>();
        // generate output
        boolean actual = new StakingSeedRule().validate(mockGrandParentHeader, mockParentHeader, mockHeader, errors);

        // test output
        assertThat(actual).isFalse();
    }
}
