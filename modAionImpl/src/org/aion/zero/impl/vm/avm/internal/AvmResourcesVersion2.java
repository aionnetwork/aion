package org.aion.zero.impl.vm.avm.internal;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmResourceFactory;

/**
 * A class that provides access to AVM version 2 resources.
 *
 * This class should be closed once it is finished with so that resources are not leaked.
 *
 * This class is not thread-safe!
 *
 * @implNote Closing an instance of this class will close the unique {@link ClassLoader} that all
 * of the resources were loaded in. This means that any new resources not already acquired cannot
 * be acquired. To be safe, this should only be closed once all resources are completely done with.
 */
public final class AvmResourcesVersion2 implements Closeable {
    private final URLClassLoader classLoader;
    public final IAvmResourceFactory resourceFactory;
    private IAionVirtualMachine avm;

    private AvmResourcesVersion2(URLClassLoader classLoader, IAvmResourceFactory resourceFactory) {
        this.classLoader = classLoader;
        this.resourceFactory = resourceFactory;
    }

    /**
     * Loads the resources associated with version 2 of the avm and returns a new instance of this
     * resource-holder class.
     */
    public static AvmResourcesVersion2 loadResources(String projectRootDir) throws IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
        URLClassLoader classLoader = newClassLoaderForAvmVersion2(projectRootDir);
        IAvmResourceFactory resourceFactory = loadAvmResourceFactory(classLoader);
        return new AvmResourcesVersion2(classLoader, resourceFactory);
    }

    /**
     * Returns the version 2 avm instance.
     *
     * @throws IllegalStateException If the avm is not currently running.
     * @return the version 2 avm instance.
     */
    public IAionVirtualMachine getAvm() {
        if (this.avm == null) {
            throw new IllegalStateException("Cannot get avm version 2 - it has not been started!");
        }
        return this.avm;
    }

    /**
     * Initializes and starts a new version 2 instance of the avm.
     *
     * @throws IllegalStateException If the avm is already running.
     */
    public void initializeAndStartNewAvm() {
        if (this.avm != null) {
            throw new IllegalStateException("The avm version 2 has already been started. Two avm's of the same version cannot both be running!");
        }
        this.avm = this.resourceFactory.createAndInitializeNewAvm();
    }

    /**
     * Shuts down the current running instance of the avm version 2.
     */
    public void shutdownAvm() {
        if (this.avm != null) {
            this.avm.shutdown();
            this.avm = null;
        }
    }

    /**
     * Returns {@code true} only if the avm version 2 is currently running.
     *
     * @return whether the avm version 2 is running or not.
     */
    public boolean isAvmRunning() {
        return this.avm != null;
    }

    /**
     * Closes the resources associated with this object.
     */
    @Override
    public void close() throws IOException {
        this.classLoader.close();
    }

    /**
     * Loads all of the required dependencies that are unique to version 2 of the avm in a new
     * classloader and returns this classloader.
     *
     * @return the classloader with the version 2 dependencies.
     */
    private static URLClassLoader newClassLoaderForAvmVersion2(String projectRootPath) throws MalformedURLException {
        File modAvmVersionJar = new File(projectRootPath + AvmDependencyInfo.jarPathForModAvmVersion2);
        File avmCoreJar = new File(projectRootPath + AvmDependencyInfo.coreJarPathVersion2);
        File avmRtJar = new File(projectRootPath + AvmDependencyInfo.rtJarPathVersion2);
        File avmUserlibJar = new File(projectRootPath + AvmDependencyInfo.userlibJarPathVersion2);
        File avmApiJar = new File(projectRootPath + AvmDependencyInfo.apiJarPathVersion2);
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL() };
        return new URLClassLoader(urls);
    }

    /**
     * Uses the provided classloader to load a new instance of {@link IAvmResourceFactory} defined
     * in the avm version 2 module.
     */
    private static IAvmResourceFactory loadAvmResourceFactory(URLClassLoader classLoader) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        Class<?> factory = classLoader.loadClass(AvmDependencyInfo.avmResourceFactoryClassNameVersion2);
        IAvmResourceFactory resourceFactory = (IAvmResourceFactory) factory.newInstance();

        // Verify that the resources were loaded by the correct classloader.
        ClassLoader resourceClassloader = resourceFactory.verifyAndReturnClassloader();
        if (resourceClassloader != classLoader) {
            classLoader.close();
            throw new IllegalStateException("The avm resources were loaded using the wrong classloader: " + resourceClassloader);
        }

        return resourceFactory;
    }
}
