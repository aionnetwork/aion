package org.aion.util.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class LogApproximator {

    // The entries in this list are log 1, log(3/2), log(5/4), log(9/8)...
    private static List<FixedPoint> logTable = new ArrayList();

    // This is the natural logarithm of 2
    private static FixedPoint log2 = FixedPoint.fromString("0.693147180559945309");
    
    private static String[] hardCodedLogs = new String[] {
            "0",
            "0.40546510810816438486",
            "0.22314355131420976486",
            "0.11778303565638345574",
            "0.06062462181643483994",
            "0.03077165866675368733",
            "0.01550418653596525448",
            "0.00778214044205494896",
            "0.00389864041565732289",
            "0.00195122013126174934",
            "0.00097608597305545892",
            "0.00048816207950135119",
            "0.00024411082752736271",
            "0.00012206286252567737",
            "0.00006103329368063853",
            "0.00003051711247318638",
            "0.00001525867264836240",
            "0.00000762936542756757",
            "0.00000381468998968589",
            "0.00000190734681382541",
            "0.00000095367386165919",
            "0.00000047683704451632",
            "0.00000023841855067986",
            "0.00000011920928244535",
            "0.00000005960464299903",
            "0.00000002980232194361",
            "0.00000001490116108283",
            "0.00000000745058056917",
            "0.00000000372529029152",
            "0.00000000186264514750",
            "0.00000000093132257418"
    };

    static {

        for (String hardCodedLog : hardCodedLogs) {
            logTable.add(FixedPoint.fromString(hardCodedLog));
        }
    }

    public static FixedPoint log(BigInteger input) {

        if (null == input || input.compareTo(BigInteger.ONE) < 0) {
            throw new IllegalArgumentException("Invalid input to log approximator");
        } else if (input.compareTo(BigInteger.ONE) == 0) {
            return FixedPoint.ZERO;
        } else {

            int bitLength = input.bitLength();

            // put x in the range [0.1, 1)
            FixedPoint x = new FixedPoint(input.shiftLeft(FixedPoint.PRECISION - bitLength));
            FixedPoint y = log2.multiplyInteger(bitLength);

            // We maintain, as an invariant, that y = log(input) - log(x)

            // Multiply x by factors in the sequence 3/2, 5/4, 9/8...

            int leftShift = 1;

            while (leftShift < logTable.size() && x.compareTo(FixedPoint.ONE) <= 0) {
                FixedPoint xPrime = x.add(x.divideByPowerOfTwo(leftShift));

                // if xPrime is less than or equal to one, this is the best factor we can multiplyInteger by
                if (xPrime.compareTo(FixedPoint.ONE) <= 0) {
                    x = xPrime;
                    y = y.subtract(logTable.get(leftShift));
                }
                // otherwise, try the next factor (eg. 1.01 was too big, so try 1.001)
                else {
                    leftShift++;
                }
            }

            // since y = log(input) - log(x), we can get closer to the real value by adding log(x) to y
            // log(x) is well approximated by (x - 1) for x ~= 1

            y = y.add(x).subtract(FixedPoint.ONE);

            return y;
        }
    }
}
