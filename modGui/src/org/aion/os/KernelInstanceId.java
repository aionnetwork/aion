package org.aion.os;

import java.io.Serializable;

/** Identifier for the OS process that is running the Aion kernel */
public class KernelInstanceId implements Serializable {
    private final long pid;
    private static final long serialVersionUID = 4L;

    /**
     * Constructor
     *
     * @param pid process id
     */
    public KernelInstanceId(long pid) {
        this.pid = pid;
    }

    /** @return process id */
    public long getPid() {
        return pid;
    }
}
