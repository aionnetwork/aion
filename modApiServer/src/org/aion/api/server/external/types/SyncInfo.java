package org.aion.api.server.external.types;

/**
 * Details the current sync state of the kernel.
 */
public class SyncInfo {
    private final boolean done;
    private final long chainStartingBlkNumber;
    private final long chainBestBlkNumber;
    private final long networkBestBlkNumber;

    public SyncInfo(boolean done, long chainStartingBlkNumber, long chainBestBlkNumber,
        long networkBestBlkNumber) {
        this.done = done;
        this.chainStartingBlkNumber = chainStartingBlkNumber;
        this.chainBestBlkNumber = chainBestBlkNumber;
        this.networkBestBlkNumber = networkBestBlkNumber;
    }

    /**
     *
     * @return {@code true} if the kernel is synced otherwise {@code false}
     */
    public boolean isDone() {
        return done;
    }

    public long getChainStartingBlkNumber() {
        return chainStartingBlkNumber;
    }

    /**
     *
     * @return the best block stored by this kernel
     */
    public long getChainBestBlkNumber() {
        return chainBestBlkNumber;
    }

    /**
     *
     * @return the best block found on the network
     */
    public long getNetworkBestBlkNumber() {
        return networkBestBlkNumber;
    }
}
