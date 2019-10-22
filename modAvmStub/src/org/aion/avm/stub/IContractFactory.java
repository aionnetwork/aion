package org.aion.avm.stub;

/**
 * A factory that produces the bytes of jar files of specified contracts.
 *
 * @implSpec This class will likely only be used for testing. Unfortunately, testing resources that
 * touch the avm directly require multi-versioned support and therefore get bundled up into the
 * source code. This isn't much harm, there's no damage you can do with this class. It is really
 * just that production code has no use for it (at the moment anyway).
 */
public interface IContractFactory {
    public enum AvmContract { HELLO_WORLD, GENERIC_CONTRACT, INTERNAL_TRANSACTION, LOG_TARGET, STATEFULNESS, TRANSACTION_HASH, META_TRANSACTION_PROXY, UNITY_STAKER_REGISTRY }

    /**
     * Returns the bytes of the specified contract in the specific encoding that the avm expects.
     *
     * @param contract The contract.
     * @return the bytes of the jar encoded for the avm.
     */
    public byte[] getDeploymentBytes(AvmContract contract);

    /**
     * Returns only the bytes of the jar file.
     *
     * NOTE: this is probably not what you want, if you are deploying a contract then you want to
     * use {@code getDeploymentBytes()} not this method!
     *
     * @param contract The contract.
     * @return the bytes of the jar file.
     */
    public byte[] getJarBytes(AvmContract contract);
}
