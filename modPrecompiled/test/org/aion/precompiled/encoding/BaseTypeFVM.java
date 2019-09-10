package org.aion.precompiled.encoding;

public abstract class BaseTypeFVM {
    public abstract byte[] serialize();

    public abstract boolean isDynamic();
}
