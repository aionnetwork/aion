package org.aion.precompiled.type;

/**
 * A class that provides static access to an externally-defined {@link IExternalCapabilitiesForPrecompiled} class.
 *
 * This class is defensively designed to ensure that we are only ever loading and unloading the
 * same capabilities and not doing anything wrong that could easily go undetected, thus the rules
 * for installing, accessing and removing the capabilities are strict.
 *
 * External capabilities must be installed prior to invoking the run method of the {@link FastVM}
 * class, and then must be removed once that call returns.
 *
 */
public final class CapabilitiesProvider {
    private static IExternalCapabilitiesForPrecompiled externalCapabilities;

    /**
     * Returns the capabilities. If there are no capabilities installed yet, an exception is thrown.
     *
     * @return the capabilities.
     */
    public static IExternalCapabilitiesForPrecompiled getExternalCapabilities() {
        if (externalCapabilities == null) {
            throw new IllegalStateException("Cannot get capabilities - it has not been set yet!");
        }
        return externalCapabilities;
    }

    /**
     * Installs the capabilities. An exception is thrown if either the capabilities to be installed
     * is null or if there is already a capabilities installed.
     *
     * @param capabilities the capabilities to install.
     */
    public static void installExternalCapabilities(IExternalCapabilitiesForPrecompiled capabilities) {
        if (capabilities == null) {
            throw new NullPointerException("Cannot set null capabilities!");
        }
        if (externalCapabilities != null) {
            throw new IllegalStateException("External capabilities are already set, cannot overwrite!");
        }
        externalCapabilities = capabilities;
    }

    /**
     * Removes the capabilities. This action is always safe.
     */
    public static void removeExternalCapabilities() {
        externalCapabilities = null;
    }
}

