package org.aion.zero.impl.vm.avm.schedule;

import org.aion.avm.stub.AvmVersion;

/**
 * A schedule that is used primarily to answer the question of which avm version to use to execute transactions at some
 * specified block number.
 */
public final class AvmVersionSchedule {
    private final long[] avmVersionBlockNumbers;

    /**
     * Constructs a new schedule for N avm versions, where N is the size of the input array.
     *
     * The long at index i of the input array is the fork-point block number for avm version i.
     *
     * @param avmVersionBlockNumbers The fork-point block numbers for each avm version.
     */
    public AvmVersionSchedule(long[] avmVersionBlockNumbers) {
        if (avmVersionBlockNumbers == null) {
            throw new NullPointerException("Cannot create schedule with null fork-point block numbers!");
        }
        if (AvmVersion.highestSupportedVersion() < avmVersionBlockNumbers.length) {
            throw new IllegalArgumentException("Provided an array of size " + avmVersionBlockNumbers.length + " but there is no avm version " + avmVersionBlockNumbers.length);
        }
        // Due to the monotonic increasing check we only have to ensure the first value is not negative.
        if (avmVersionBlockNumbers.length > 0 && avmVersionBlockNumbers[0] < 0) {
            throw new IllegalArgumentException("All fork-point block numbers must be strictly non-negative!");
        }
        for (int i = 0; i < (avmVersionBlockNumbers.length - 1); i++) {
            if (avmVersionBlockNumbers[i] >= avmVersionBlockNumbers[i + 1]) {
                throw new IllegalArgumentException("All fork-point block numbers must be in strictly increasing order! Violated by indices " + i + " and " + (i + 1));
            }
        }
        this.avmVersionBlockNumbers = avmVersionBlockNumbers;
    }

    /**
     * Returns the avm version that should be used to execute transactions at the specified block number.
     *
     * Returns null if no avm version is active that the specified block number.
     *
     * @param blockNumber The block number to query.
     * @return the avm version to run transactions with.
     */
    public AvmVersion whichVersionToRunWith(long blockNumber) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Invalid block number: " + blockNumber + ". Block number must be non-negative!");
        }

        if ((this.avmVersionBlockNumbers.length > 0) && (this.avmVersionBlockNumbers[this.avmVersionBlockNumbers.length - 1] <= blockNumber)) {
            return AvmVersion.fromNumber(this.avmVersionBlockNumbers.length);
        }

        for (int i = 0; i < (this.avmVersionBlockNumbers.length - 1); i++) {
            if ((this.avmVersionBlockNumbers[i] <= blockNumber) && (blockNumber < this.avmVersionBlockNumbers[i + 1])) {
                return AvmVersion.fromNumber(i + 1);
            }
        }

        return null;
    }
}
