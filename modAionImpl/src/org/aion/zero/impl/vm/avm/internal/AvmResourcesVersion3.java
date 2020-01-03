package org.aion.zero.impl.vm.avm.internal;

import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmResourceFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A class that provides access to AVM version 3 resources.
 *
 * This class should be closed once it is finished with so that resources are not leaked.
 *
 * This class is not thread-safe!
 *
 * @implNote Closing an instance of this class will close the unique {@link ClassLoader} that all
 * of the resources were loaded in. This means that any new resources not already acquired cannot
 * be acquired. To be safe, this should only be closed once all resources are completely done with.
 */
public final class AvmResourcesVersion3 implements Closeable {
    private final URLClassLoader classLoader;
    public final IAvmResourceFactory resourceFactory;
    private IAionVirtualMachine avm;

    private AvmResourcesVersion3(URLClassLoader classLoader, IAvmResourceFactory resourceFactory) {
        this.classLoader = classLoader;
        this.resourceFactory = resourceFactory;
    }

    /**
     * Loads the resources associated with version 3 of the avm and returns a new instance of this
     * resource-holder class.
     */
    public static AvmResourcesVersion3 loadResources(String projectRootDir) throws IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
        URLClassLoader classLoader = newClassLoaderForAvmVersion3(projectRootDir);
        IAvmResourceFactory resourceFactory = loadAvmResourceFactory(classLoader);
        return new AvmResourcesVersion3(classLoader, resourceFactory);
    }

    /**
     * Returns the version 3 avm instance.
     *
     * @throws IllegalStateException If the avm is not currently running.
     * @return the version 3 avm instance.
     */
    public IAionVirtualMachine getAvm() {
        if (this.avm == null) {
            throw new IllegalStateException("Cannot get avm version 3 - it has not been started!");
        }
        return this.avm;
    }

    /**
     * Initializes and starts a new version 3 instance of the avm.
     *
     * @throws IllegalStateException If the avm is already running.
     */
    public void initializeAndStartNewAvm() {
        if (this.avm != null) {
            throw new IllegalStateException("The avm version 3 has already been started. Two avm's of the same version cannot both be running!");
        }
        this.avm = this.resourceFactory.createAndInitializeNewAvm();
    }

    /**
     * Shuts down the current running instance of the avm version 3.
     */
    public void shutdownAvm() {
        if (this.avm != null) {
            this.avm.shutdown();
            this.avm = null;
        }
    }

    /**
     * Returns {@code true} only if the avm version 3 is currently running.
     *
     * @return whether the avm version 3 is running or not.
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
     * Loads all of the required dependencies that are unique to version 3 of the avm in a new
     * classloader and returns this classloader.
     *
     * @return the classloader with the version 3 dependencies.
     */
    private static URLClassLoader newClassLoaderForAvmVersion3(String projectRootPath) throws MalformedURLException {
        File modAvmVersionJar = new File(projectRootPath + AvmDependencyInfo.jarPathForModAvmVersion3);
        File avmCoreJar = new File(projectRootPath + AvmDependencyInfo.coreJarPathVersion3);
        File avmRtJar = new File(projectRootPath + AvmDependencyInfo.rtJarPathVersion3);
        File avmUserlibJar = new File(projectRootPath + AvmDependencyInfo.userlibJarPathVersion3);
        File avmToolingJar = new File(projectRootPath + AvmDependencyInfo.toolingJarPathVersion3);
        File avmApiJar = new File(projectRootPath + AvmDependencyInfo.apiJarPathVersion3);
        File avmUtilitiesJar = new File(projectRootPath + AvmDependencyInfo.utilitiesJarPathVersion3);
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmToolingJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL(), avmUtilitiesJar.toURI().toURL() };
        return new URLClassLoader(urls);
    }

    /**
     * Uses the provided classloader to load a new instance of {@link IAvmResourceFactory} defined
     * in the avm version 3 module.
     */
    private static IAvmResourceFactory loadAvmResourceFactory(URLClassLoader classLoader) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        Class<?> factory = classLoader.loadClass(AvmDependencyInfo.avmResourceFactoryClassNameVersion3);
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
