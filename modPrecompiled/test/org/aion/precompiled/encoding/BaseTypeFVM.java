package org.aion.precompiled.encoding;

import java.util.List;
import java.util.Optional;

public abstract class BaseTypeFVM {
    public abstract byte[] serialize();

    public abstract boolean isDynamic();

    /**
     * Checks if the type has sub-elements, then the {@link Optional} will
     * contain a list of elements, otherwise it will be {@link Optional#empty()}
     */
    public abstract Optional<List<BaseTypeFVM>> getEntries();
}
