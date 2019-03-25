package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import org.aion.zero.exceptions.HeaderStructureException;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(ChainConfigurationTest.class);

    @Mock A0BlockHeader header;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Ignore // To be re-enabled later
    @Test
    public void testValidation() throws HeaderStructureException {
        int n = 210;
        int k = 9;
        byte[] nonce = {
            1, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0
        };
        // setup mock
        //        A0BlockHeader.Builder builder = new A0BlockHeader.Builder();
        //        builder.withDifficulty(BigInteger.valueOf(1).toByteArray());
        //        builder.withNonce(nonce);
        //        builder.withTimestamp(12345678910L);
        //        A0BlockHeader header = builder.build();
        //
        //        // Static header bytes (portion of header which does not change per equihash
        // iteration)
        //        byte [] staticHeaderBytes = header.getStaticHash();
        //
        //        // Dynamic header bytes
        //        long timestamp = header.getTimestamp();
        //
        //        // Dynamic header bytes (portion of header which changes each iteration0
        //        byte[] dynamicHeaderBytes = ByteUtil.longToBytes(timestamp);
        //
        //        BigInteger target = header.getPowBoundaryBI();
        //
        //        //Merge H(static) and dynamic portions into a single byte array
        //        byte[] inputBytes = new byte[staticHeaderBytes.length +
        // dynamicHeaderBytes.length];
        //        System.arraycopy(staticHeaderBytes, 0, inputBytes, 0 , staticHeaderBytes.length);
        //        System.arraycopy(dynamicHeaderBytes, 0, inputBytes, staticHeaderBytes.length,
        // dynamicHeaderBytes.length);
        //
        //        Equihash equihash = new Equihash(n, k);
        //
        //        int[][] solutions;
        //
        //        // Generate 3 solutions
        //        solutions = equihash.getSolutionsForNonce(inputBytes, header.getNonce());
        //
        //        // compress solution
        //        byte[] compressedSolution = EquiUtils.getMinimalFromIndices(solutions[0],
        // n/(k+1));
        //        header.setSolution(compressedSolution);
        //
        //        ChainConfiguration chainConfig = new ChainConfiguration();
        //        BlockHeaderValidator<A0BlockHeader> blockHeaderValidator =
        // chainConfig.createBlockHeaderValidator();
        //        blockHeaderValidator.validate(header, log);
    }

    // assuming 100000 block ramp
    @Test
    public void testRampUpFunctionBoundaries() {
        long upperBound = 259200L;

        ChainConfiguration config = new ChainConfiguration();
        BigInteger increment =
                config.getConstants()
                        .getBlockReward()
                        .subtract(config.getConstants().getRampUpStartValue())
                        .divide(BigInteger.valueOf(upperBound))
                        .add(config.getConstants().getRampUpStartValue());

        // UPPER BOUND
        when(header.getNumber()).thenReturn(upperBound);
        BigInteger blockReward259200 = config.getRewardsCalculator().calculateReward(header.getNumber());

        when(header.getNumber()).thenReturn(upperBound + 1);
        BigInteger blockReward259201 = config.getRewardsCalculator().calculateReward(header.getNumber());

        // check that at the upper bound of our range (which is not included) blockReward is capped
        assertThat(blockReward259200).isEqualTo(new BigInteger("1497989283243258292"));

        // check that for the block after, the block reward is still the same
        assertThat(blockReward259201).isEqualTo(config.getConstants().getBlockReward());

        // check that for an arbitrarily large block, the block reward is still the same
        when(header.getNumber()).thenReturn(upperBound + 100000);
        BigInteger blockUpper = config.getRewardsCalculator().calculateReward(header.getNumber());
        assertThat(blockUpper).isEqualTo(config.getConstants().getBlockReward());

        // LOWER BOUNDS
        when(header.getNumber()).thenReturn(0l);
        BigInteger blockReward0 = config.getRewardsCalculator().calculateReward(header.getNumber());
        assertThat(blockReward0).isEqualTo(new BigInteger("748994641621655092"));

        // first block (should have gas value of increment)
        when(header.getNumber()).thenReturn(1l);
        BigInteger blockReward1 = config.getRewardsCalculator().calculateReward(header.getNumber());
        assertThat(blockReward1).isEqualTo(increment);
    }
}
