package org.aion.zero.impl.vm;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.aion.zero.impl.vm.avm.internal.AvmDependencyInfo;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.zero.impl.vm.avm.AvmProvider;

/**
 * A class that provides access to underlying test-only avm multi-versioned resources.
 *
 * @implNote The factories that are exposed by this provider are loaded in their own unique
 * classloader. Namely, these will not be loaded in the same classloader as the actual
 * {@link AvmProvider} uses to load the factories.
 */
public final class TestResourceProvider implements Closeable {
    private final URLClassLoader classLoaderForVersion1;
    private final URLClassLoader classLoaderForVersion2;
    public final IAvmResourceFactory factoryForVersion1;
    public final IAvmResourceFactory factoryForVersion2;

    private TestResourceProvider(URLClassLoader classLoaderForVersion1, IAvmResourceFactory version1, URLClassLoader classLoaderForVersion2, IAvmResourceFactory version2) {
        this.classLoaderForVersion1 = classLoaderForVersion1;
        this.classLoaderForVersion2 = classLoaderForVersion2;
        this.factoryForVersion1 = version1;
        this.factoryForVersion2 = version2;
    }

    /**
     * Initializes the test resources for the two avm versions and creates a new provider with
     * those loaded resources.
     *
     * @return a new test provider.
     */
    public static TestResourceProvider initializeAndCreateNewProvider(String projectRootDirectory) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        URLClassLoader version1 = newClassLoaderForAvmVersion(projectRootDirectory, AvmVersion.VERSION_1);
        URLClassLoader version2 = newClassLoaderForAvmVersion(projectRootDirectory, AvmVersion.VERSION_2);
        IAvmResourceFactory factory1 = loadAvmResourceFactory(version1, AvmVersion.VERSION_1);
        IAvmResourceFactory factory2 = loadAvmResourceFactory(version2, AvmVersion.VERSION_2);
        return new TestResourceProvider(version1, factory1, version2, factory2);
    }

    @Override
    public void close() throws IOException {
        this.classLoaderForVersion1.close();
        this.classLoaderForVersion2.close();
    }

    /**
     * Loads all of the required dependencies that are unique to the specified versionof the avm
     * in a new classloader and returns this classloader.
     *
     * @return the classloader with the specified version dependencies.
     */
    private static URLClassLoader newClassLoaderForAvmVersion(String projectRootPath, AvmVersion version) throws MalformedURLException {
        if (version == AvmVersion.VERSION_1) {
            return newClassLoaderForAvmVersion1(projectRootPath);
        }
        if (version == AvmVersion.VERSION_2) {
            return newClassLoaderForAvmVersion2(projectRootPath);
        }
        throw new IllegalArgumentException("AVM Version not supported: " + version.name());
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
        File avmApiJar = new File(projectRootPath + AvmDependencyInfo.apiJarPathVersion1);
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL() };
        return new URLClassLoader(urls);
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
        File avmUtilitiesJar = new File(projectRootPath + AvmDependencyInfo.utilitiesJarPathVersion2);
        URL[] urls = new URL[]{ modAvmVersionJar.toURI().toURL(), avmCoreJar.toURI().toURL(), avmRtJar.toURI().toURL(), avmUserlibJar.toURI().toURL(), avmApiJar.toURI().toURL(), avmUtilitiesJar.toURI().toURL() };
        return new URLClassLoader(urls);
    }

    /**
     * Uses the provided classloader to load a new instance of {@link IAvmResourceFactory} defined
     * in the avm version 1 module.
     */
    private static IAvmResourceFactory loadAvmResourceFactory(URLClassLoader classLoader, AvmVersion version) throws IllegalAccessException, InstantiationException, ClassNotFoundException, IOException {
        Class<?> factory = classLoader.loadClass(version == AvmVersion.VERSION_1 ? AvmDependencyInfo.avmResourceFactoryClassNameVersion1 : AvmDependencyInfo.avmResourceFactoryClassNameVersion2);
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
