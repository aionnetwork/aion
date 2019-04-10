package org.aion.mcf.core;

/**
 * Import results that can be returned when importing blocks using Fast Sync strategy.
 *
 * @author Alexandra Roatis
 */
public enum FastImportResult {
    IMPORTED,
    KNOWN,
    NO_CHILD,
    INVALID_BLOCK;

    public boolean isSuccessful() {
        return equals(IMPORTED);
    }

    public boolean isKnown() {
        return equals(KNOWN);
    }
}
