package org.aion.gui.model;

import static org.mockito.Mockito.when;

import org.aion.api.IAionAPI;

/**
 * A helper for testing, not an actual test.
 *
 * This exists only so we can keep {@link KernelConnection#getApi()} package-private while mocking
 * that method in tests in a different package.
 */
public class KernelConnectionMockSetter {

    public static void setApiOfMockKernelConnection(KernelConnection kc, IAionAPI api) {
        when(kc.getApi()).thenReturn(api);
    }
}
