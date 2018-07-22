package org.aion.api.server.http;

import java.util.Optional;

// @ThreadSafe
public enum RpcServerVendor {
    NANO, // nano httpd
    UNDERTOW; // undertow (jboss)

    public static Optional<RpcServerVendor> fromString(String _vendor) {
        if (_vendor == null) return Optional.empty();

        for (RpcServerVendor vendor : values())
            if (vendor.name().equalsIgnoreCase(_vendor))
                return Optional.of(vendor);

        return Optional.empty();
    }
}
