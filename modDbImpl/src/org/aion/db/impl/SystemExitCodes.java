package org.aion.db.impl;

/** Error codes to be used when calling {@link System#exit(int)} from the kernel. */
public class SystemExitCodes {

    public static final int NORMAL = 0;
    public static final int OUT_OF_DISK_SPACE = 1;
    public static final int DATABASE_CORRUPTION = 2;
    public static final int FATAL_VM_ERROR = 3;
    public static final int INITIALIZATION_ERROR = 4;
}
