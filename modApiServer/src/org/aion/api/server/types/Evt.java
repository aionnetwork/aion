package org.aion.api.server.types;

import static org.aion.api.server.types.Fltr.Type;

public abstract class Evt {

    public abstract Type getType();

    public abstract Object toJSON();
}
