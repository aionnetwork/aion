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
     * Returns a new external state builder.
     *
     * @return a new builder.
     */
    public IExternalStateBuilder newExternalStateBuilder();

    /**
     * Verifies that all of the jars loaded by this factory were loaded by the same classloader
     * and then returns that classloader so that the caller can check against it.
     *
     * @return the classloader that loaded all the resources.
     */
    public ClassLoader verifyAndReturnClassloader();
}
