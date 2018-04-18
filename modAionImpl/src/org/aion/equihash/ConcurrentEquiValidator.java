package org.aion.equihash;

import org.aion.crypto.hash.Blake2b;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.concurrent.Callable;

import static org.aion.base.util.ByteUtil.intToBytesLE;
import static org.aion.base.util.ByteUtil.merge;

public class ConcurrentEquiValidator {
    private int n;
    private int k;
    private int indicesPerHashOutput;
    private int indicesHashLength;
    private int hashOutput;
    private int collisionBitLength;
    private int collisionByteLength;
    private int solutionWidth;
    private HashSet<Integer> indexSet;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());
    private Blake2b.Param initState;

    public ConcurrentEquiValidator(int n, int k) {
        this.n = n;
        this.k = k;
        this.indicesPerHashOutput = 512 / n;
        this.indicesHashLength = (n + 7) / 8;
        this.hashOutput = indicesPerHashOutput * indicesHashLength;
        this.collisionBitLength = n / (k + 1);
        this.collisionByteLength = (collisionBitLength + 7) / 8;
        this.solutionWidth = (1 << k) * (collisionBitLength + 1) / 8;
        this.initState = this.InitialiseState();
    }

    /**
     * Initialize Equihash parameters; current implementation uses default
     * equihash parameters. Set Personalization to "AION0PoW" + n to k where n
     * and k are in little endian byte order.
     *
     * @return a Param object containing Blake2b parameters.
     */
    private Blake2b.Param InitialiseState() {
        Blake2b.Param p = new Blake2b.Param();
        byte[] personalization = merge("AION0PoW".getBytes(), merge(intToBytesLE(n), intToBytesLE(k)));
        p.setPersonal(personalization);
        p.setDigestLength(hashOutput);

        return p;
    }

    /*
     * Check if duplicates are present in the solutions index array
     */
    private boolean hasDuplicate(int[] indices) {
        for(int index: indices) {
            if(!indexSet.add(index))
                return true;
        }
        indexSet.clear();

        return false;
    }

    private class ValidationRunnable implements Callable<Boolean> {



        @Override
        public Boolean call() throws Exception {
            return null;
        }
    }

}
