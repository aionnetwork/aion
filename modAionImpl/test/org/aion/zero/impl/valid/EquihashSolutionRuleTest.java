// package org.aion.zero.impl.valid;
//
// import ByteArrayWrapper;
// import org.aion.equihash.EquiUtils;
// import org.aion.equihash.EquiValidator;
// import org.aion.equihash.Equihash;
// import org.aion.equihash.OptimizedEquiValidator;
// import org.aion.zero.impl.valid.EquihashSolutionRule;
// import org.aion.zero.types.A0BlockHeader;
// import org.junit.Before;
// import org.junit.Test;
// import org.mockito.MockitoAnnotations;
//
// import java.math.BigInteger;
//
// import static com.google.common.truth.Truth.assertThat;
// import static org.junit.Assert.assertThat;
//
// public class EquihashSolutionRuleTest {
//    @Before
//    public void before() {
//        MockitoAnnotations.initMocks(this);
//    }
//
//    // perhaps we should generate a solution by hand
//    @Test
//    public void testProperSolution() {
//        // given that all our inputs are deterministic, for given nonce, there should always
//        // be a valid output solution
//        final int n = 210;
//        final int k = 9;
//        final BigInteger givenNonce = new BigInteger("21");
//
//        // assume a 32-byte nonce (fixed)
//        byte[] unpaddedNonceBytes = givenNonce.toByteArray();
//        byte[] nonceBytes = new byte[32];
//
//        System.arraycopy(unpaddedNonceBytes, 0, nonceBytes, 32 - unpaddedNonceBytes.length,
// unpaddedNonceBytes.length);
//        System.out.println(new ByteArrayWrapper(nonceBytes));
//
//        A0BlockHeader header = new A0BlockHeader.Builder().build();
//        header.setNonce(nonceBytes);
//        byte[] headerBytes = header.getHeaderBytes(true);
//
//        Equihash equihash = new Equihash(n, k);
//        int[][] solutions = equihash.getSolutionsForNonce(headerBytes, header.getNonce());
//
//        byte[] compressedSolution = (new EquiUtils()).getMinimalFromIndices(solutions[0],
// n/(k+1));
//        header.setSolution(compressedSolution);
//
////        EquihashSolutionRule rule = new EquihashSolutionRule(new OptimizedEquiValidator(n, k));
////        boolean result = rule.validate(header);
////        assertThat(result).isTrue();
////        assertThat(rule.getErrors()).isEmpty();
//    }
// }
