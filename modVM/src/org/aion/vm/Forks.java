package org.aion.vm;

public class Forks {

    private static final long SEPTEMBER_FORK = 1000000L;
    public static Boolean TEST_SEPTEMBER_FORK = null;

    /**
     * Returns whether the fork in September is enabled. This fork was created
     * to fix the event logs not reverted for failed transactions issue.
     *
     * @param blockNumber
     * @return true if enabled; otherwise, false
     */
    public static boolean isSeptemberForkEnabled(long blockNumber) {
        return TEST_SEPTEMBER_FORK != null ? TEST_SEPTEMBER_FORK : blockNumber >= SEPTEMBER_FORK;
    }

    public static void clearTestState() {
        TEST_SEPTEMBER_FORK = null;
    }
}
