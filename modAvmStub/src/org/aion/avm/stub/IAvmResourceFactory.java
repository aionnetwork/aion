package org.aion.avm.stub;

/**
 * A factory used to fetch particular AVM resources exposed by a specific AVM version.
 *
 * Any implementation of this interface must have a public no-arguments constructor!
 *
 * @apiNote This factory exists so that we have only a single point of reflection in the code, to
 * get a hold of this object. Once this factory is in our hands we can acquire any of the resources
 * we want.
 */
public interface IAvmResourceFactory {

    /**
     * Creates and initializes a new AVM, returning it.
     *
     * @return a new AVM.
     */
    public IAionVirtualMachine createAndInitializeNewAvm();

    /**
     * Creates and initializes a new AVM with coinbase lock feature, returning it.
     *
     * @return a new AVM.
     */
    public IAionVirtualMachine createAndInitializeNewAvmWithCoinbaseLock();

    /**
     * Returns a new external state builder.
     *
     * @return a new builder.
     */
    public IAvmExternalStateBuilder newExternalStateBuilder();

    /**
     * Verifies that all of the jars loaded by this factory were loaded by the same classloader
     * and then returns that classloader so that the caller can check against it.
     *
     * @return the classloader that loaded all the resources.
     */
    public ClassLoader verifyAndReturnClassloader();

    /**
     * Returns a new contract factory.
     *
     * @return a new contract factory.
     */
    public IContractFactory newContractFactory();

    /**
     * Returns a new streaming encoder.
     *
     * @return a new streaming encoder.
     */
    public IStreamingEncoder newStreamingEncoder();

    /**
     * Returns a new decoder that will operate on the given encoding.
     *
     * @param encoding The encoding to decode.
     * @return the decoder.
     */
    public IDecoder newDecoder(byte[] encoding);
}
