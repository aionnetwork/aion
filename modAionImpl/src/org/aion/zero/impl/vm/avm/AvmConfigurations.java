package org.aion.zero.impl.vm.avm;

import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;
import org.aion.avm.stub.IEnergyRules;
import org.aion.zero.impl.vm.common.TxNrgRule;

/**
 * The class that holds all details pertaining to how the avm is set up.
 */
public final class AvmConfigurations {
    private static boolean isInitialized = false;
    private static boolean isReadOnly = false;
    private static AvmVersionSchedule multiVersionSchedule = null;
    private static String projectRootDir = null;
    private static IEnergyRules energyRules = (t, l) -> (t == IEnergyRules.TransactionType.CREATE) ? TxNrgRule.isValidNrgContractCreate(l) : TxNrgRule.isValidNrgTx(l);
    // The AVM does check the energy requirement regarding the transaction data context. Therefor We only check the basic rule in here.
    private static IEnergyRules energyRulesAfterUnityFork =
            (t, l) ->
                    (t == IEnergyRules.TransactionType.CREATE)
                            ? TxNrgRule.isValidNrgContractCreateAfterUnity(l, null)
                            : TxNrgRule.isValidNrgTxAfterUnity(l, null);

    private AvmConfigurations() {}

    /**
     * Initializes the avm configuration details as read-only. In other words, these configuration
     * details will not be overwritable once initialized.
     *
     * This is how these details should be initialized during production code, so that this is
     * done exactly once during the boot phase and is never worried about again.
     *
     * If this method is called then any subsequent calls to any initialization method will fail.
     *
     * @param schedule The multi-version schedule.
     * @param projectRootDirectory The directory of the project root.
     */
    public static void initializeConfigurationsAsReadOnly(AvmVersionSchedule schedule, String projectRootDirectory) {
        if (isReadOnly) {
            throw new IllegalStateException("Cannot initialize avm configurations - this class has already been initialized as a Read-Only class!");
        }
        if (schedule == null) {
            throw new NullPointerException("Cannot initialize using a null schedule!");
        }
        if (projectRootDirectory == null) {
            throw new NullPointerException("Cannot initialize using a null projectRootDirectory!");
        }

        isReadOnly = true;
        isInitialized = true;
        multiVersionSchedule = schedule;
        projectRootDir = projectRootDirectory;
    }

    /**
     * Initializes the avm configuration details as read and writeable. In other words, these
     * configuration details may be overwritten after calling this method.
     *
     * This is how these details should be initialized during tests, so that each test can install
     * its only configuration details.
     *
     * @param schedule The multi-version schedule.
     * @param projectRootDirectory The directory of the project root.
     */
    public static void initializeConfigurationsAsReadAndWriteable(AvmVersionSchedule schedule, String projectRootDirectory) {
        if (isReadOnly) {
            throw new IllegalStateException("Cannot initialize avm configurations - this class has already been initialized as a Read-Only class!");
        }
        if (schedule == null) {
            throw new NullPointerException("Cannot initialize using a null schedule!");
        }
        if (projectRootDirectory == null) {
            throw new NullPointerException("Cannot initialize using a null projectRootDirectory!");
        }

        isInitialized = true;
        multiVersionSchedule = schedule;
        projectRootDir = projectRootDirectory;
    }

    /**
     * Clears the configuration details, if there were any, so that the class is no longer in an
     * initialized state.
     *
     * This method will fail if the class was initialized as read-only, in which case clearing is
     * an invalid operation.
     */
    public static void clear() {
        if (isReadOnly) {
            throw new IllegalStateException("Cannot clear the avm configurations - this class has been initialized as a Read-Only class!");
        }

        isInitialized = false;
        multiVersionSchedule = null;
        projectRootDir = null;
    }

    public static AvmVersionSchedule getAvmVersionSchedule() {
        if (!isInitialized) {
            throw new IllegalStateException("Cannot get schedule - this class has not been initialized yet!");
        }
        return multiVersionSchedule;
    }

    public static String getProjectRootDirectory() {
        if (!isInitialized) {
            throw new IllegalStateException("Cannot get project root directory - this class has not been initialized yet!");
        }
        return projectRootDir;
    }

    public static IEnergyRules getEnergyLimitRules() {
        if (!isInitialized) {
            throw new IllegalStateException("Cannot get energy limit rules - this class has not been initialized yet!");
        }
        return energyRules;
    }

    public static IEnergyRules getEnergyLimitRulesAfterUnityFork() {
        if (!isInitialized) {
            throw new IllegalStateException("Cannot get energy limit rules after unity fork - this class has not been initialized yet!");
        }
        return energyRulesAfterUnityFork;
    }
}
