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
 * A class that provides access to AVM version 1 resources.
 *
 * This class should be closed once it is finished with so that resources are not leaked.
 *
 * This class is not thread-safe!
 *
 * @implNote Closing an instance of this class will close the unique {@link ClassLoader} that all
 * of the resources were loaded in. This means that any new resources not already acquired cannot
 * be acquired. To be safe, this should only be closed once all resources are completely done with.
 */
public final class AvmResourcesVersion1 implements Closeable {
    private final URLClassLoader classLoader;
    public final IAvmResourceFactory resourceFactory;
    private IAionVirtualMachine avm;

    private AvmResourcesVersion1(URLClassLoader classLoader, IAvmResourceFactory resourceFactory) {
        this.classLoader = classLoader;
        this.resourceFactory = resourceFactory;
    }

    /**
     * Loads the resources associated with version 1 of the avm and returns a new instance of this
     * resource-holder class.
     */
    public static AvmResourcesVersion1 loadResources(String projectRootDir) throws IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
        URLClassLoader classLoader = newClassLoaderForAvmVersion1(projectRootDir);
        IAvmResourceFactory resourceFactory = loadAvmResourceFactory(classLoader);
        return new AvmResourcesVersion1(classLoader, resourceFactory);
    }

    /**
     * Returns the version 1 avm instance.
     *
     * @throws IllegalStateException If the avm is not currently running.
     * @return the version 1 avm instance.
     */
    public IAionVirtualMachine getAvm() {
        if (this.avm == null) {
            throw new IllegalStateException("Cannot get avm version 1 - it has not been started!");
        }
        return this.avm;
    }

    /**
     * Initializes and starts a new version 1 instance of the avm.
     *
     * @throws IllegalStateException If the avm is already running.
     */
    public void initializeAndStartNewAvm() {
        if (this.avm != null) {
            throw new IllegalStateException("The avm version 1 has already been started. Two avm's of the same version cannot both be running!");
        }
        this.avm = this.resourceFactory.createAndInitializeNewAvm();
    }

    /**
     * Shuts down the current running instance of the avm version 1.
     */
    public void shutdownAvm() {
        if (this.avm != null) {
            this.avm.shutdown();
            this.avm = null;
        }
    }

    /**
     * Returns {@code true} only if the avm version 1 is currently running.
     *
     * @return whether the avm version 1 is running or not.
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
     * Loads all of the required dependencies that are unique to version 1 of the avm in a new
     * classloader and returns this classloader.
     *
     * @return the classloader with the version 1 dependencies.
     */
    private static URLClassLoader newClassLoaderForAvmVersion1(String projectRootPath) throws MalformedURLException {
        File modAvmVersionJar = new File(projectRootPath + AvmDependencyInfo.jarPathForModAvmVersion1);
        File avmCoreJar = new File(projectRootPath + AvmDependencyInfo.coreJarPathVersion1);
        File avmRtJar = new File(projectRootPath + AvmDependencyInfo.rtJarPathVersion1);
        File avmUserlibJar = new File(projectRootPath + AvmDependencyInfo.userlibJarPathVersion1);
        File avmToolingJar = new File(projectRootPath + AvmDependencyInfo.toolingJarPathVersion1);
        File avmApiJar = new File(projectRootPath + AvmDependencyInfo.apiJarPathVersion1);
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmToolingJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL() };
        return new URLClassLoader(urls);
    }

    /**
     * Uses the provided classloader to load a new instance of {@link IAvmResourceFactory} defined
     * in the avm version 1 module.
     */
    private static IAvmResourceFactory loadAvmResourceFactory(URLClassLoader classLoader) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        Class<?> factory = classLoader.loadClass(AvmDependencyInfo.avmResourceFactoryClassNameVersion1);
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
