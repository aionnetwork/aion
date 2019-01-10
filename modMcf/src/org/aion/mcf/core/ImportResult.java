package org.aion.mcf.core;

/** Import Result Enum */
public enum ImportResult {
    IMPORTED_BEST,
    IMPORTED_NOT_BEST,
    EXIST,
    NO_PARENT,
    INVALID_BLOCK,
    CONSENSUS_BREAK;

    public boolean isSuccessful() {
        return equals(IMPORTED_BEST) || equals(IMPORTED_NOT_BEST);
    }

    public boolean isBest() {
        return equals(IMPORTED_BEST);
    }

    public boolean isNotBest() {
        return equals(IMPORTED_NOT_BEST);
    }

    public boolean isStored() {
        return equals(EXIST) || isSuccessful();
    }

    public boolean isValid() {
        return !(equals(INVALID_BLOCK) || equals(CONSENSUS_BREAK));
    }
}
