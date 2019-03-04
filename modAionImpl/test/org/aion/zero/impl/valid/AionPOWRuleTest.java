package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AionPOWRuleTest {

    @Mock A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBelowPOWMaximumBoundary() {
        final byte[] predefinedHash = new byte[32];
        final long predefinedTimestamp = 0;
        final byte[] predefinedNonce = new byte[32];
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.ONE;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI())
                .thenReturn(BigInteger.ONE.shiftLeft(256).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testBelowPOWHalfBoundary() {
        // yes this will produce a value smaller than the boundary
        final byte[] predefinedHash =
                ByteUtil.hexStringToBytes(
                        "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        final long predefinedTimestamp = 1;
        final byte[] predefinedNonce =
                ByteUtil.hexStringToBytes(
                        "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.TWO;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI())
                .thenReturn(BigInteger.ONE.shiftLeft(256).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testAbovePOWHalfBoundary() {
        // this produces a result larger than the boundary
        final byte[] predefinedHash = new byte[32];
        final long predefinedTimestamp = 0;
        final byte[] predefinedNonce = new byte[32];
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.TWO;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI())
                .thenReturn(BigInteger.ONE.shiftLeft(128).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isFalse();
        assertThat(errors).isNotEmpty();
    }
}
