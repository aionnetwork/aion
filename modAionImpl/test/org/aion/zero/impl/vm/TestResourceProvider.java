package org.aion.zero.impl.vm;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.aion.avm.provider.internal.AvmDependencyInfo;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;

/**
 * A class that provides access to underlying test-only avm multi-versioned resources.
 *
 * @implNote The factories that are exposed by this provider are loaded in their own unique
 * classloader. Namely, these will not be loaded in the same classloader as the actual
 * {@link org.aion.avm.provider.AvmProvider} uses to load the factories.
 */
public final class TestResourceProvider implements Closeable {
    private final URLClassLoader classLoaderForVersion1;
    public final IAvmResourceFactory factoryForVersion1;

    private TestResourceProvider(URLClassLoader classLoaderForVersion1, IAvmResourceFactory version1) {
        this.classLoaderForVersion1 = classLoaderForVersion1;
        this.factoryForVersion1 = version1;
    }

    /**
     * Initializes the test resources for the two avm versions and creates a new provider with
     * those loaded resources.
     *
     * @return a new test provider.
     */
    public static TestResourceProvider initializeAndCreateNewProvider(String projectRootDirectory) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        URLClassLoader version1 = newClassLoaderForAvmVersion(projectRootDirectory, AvmVersion.VERSION_1);
        IAvmResourceFactory factory1 = loadAvmResourceFactory(version1, AvmVersion.VERSION_1);
        return new TestResourceProvider(version1, factory1);
    }

    @Override
    public void close() throws IOException {
        this.classLoaderForVersion1.close();
    }

    /**
     * Loads all of the required dependencies that are unique to version 1 of the avm in a new
     * classloader and returns this classloader.
     *
     * @return the classloader with the version 1 dependencies.
     */
    private static URLClassLoader newClassLoaderForAvmVersion(String projectRootPath, AvmVersion version) throws MalformedURLException {
        File modAvmVersionJar = new File(projectRootPath + (version == AvmVersion.VERSION_1 ? AvmDependencyInfo.jarPathForModAvmVersion1 : null));
        File avmCoreJar = new File(projectRootPath + (version == AvmVersion.VERSION_1 ? AvmDependencyInfo.coreJarPathVersion1 : null));
        File avmRtJar = new File(projectRootPath + (version == AvmVersion.VERSION_1 ? AvmDependencyInfo.rtJarPathVersion1 : null));
        File avmUserlibJar = new File(projectRootPath + (version == AvmVersion.VERSION_1 ? AvmDependencyInfo.userlibJarPathVersion1 : null));
        File avmApiJar = new File(projectRootPath + (version == AvmVersion.VERSION_1 ? AvmDependencyInfo.apiJarPathVersion1 : null));
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL() };
        return new URLClassLoader(urls);
    }

    /**
     * Uses the provided classloader to load a new instance of {@link IAvmResourceFactory} defined
     * in the avm version 1 module.
     */
    private static IAvmResourceFactory loadAvmResourceFactory(URLClassLoader classLoader, AvmVersion version) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        Class<?> factory = classLoader.loadClass(version == AvmVersion.VERSION_1 ? AvmDependencyInfo.avmResourceFactoryClassNameVersion1 : null);
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
