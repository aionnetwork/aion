package org.aion.vm;

public class Forks {

    private static final long JUNE_FORK = 167691L;
    public static Boolean TEST_JUNE_FORK = null;


    private static final long SEPTEMBER_FORK = 1000000L;
    public static Boolean TEST_SEPTEMBER_FORK = null;

    /**
     * Returns whether the fork in June is enabled. This fork was created to fix
     * the address issue for CREATE opcode and the nonce issue.
     *
     * @param blockNumber
     * @return true if enabled; otherwise, false
     */
    public static boolean isJuneForkEnabled(long blockNumber) {
        return TEST_JUNE_FORK != null ? TEST_JUNE_FORK : blockNumber >= JUNE_FORK;
    }

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
        TEST_JUNE_FORK = null;
        TEST_SEPTEMBER_FORK = null;
    }
}
