package org.aion.precompiled.contracts.ATB;

// utility helpers
public enum ErrCode {
    NO_ERROR(0x0),
    NOT_OWNER(0x1),
    NOT_NEW_OWNER(0x2),
    RING_LOCKED(0x3),
    RING_NOT_LOCKED(0x4),
    RING_MEMBER_EXISTS(0x5),
    RING_MEMBER_NOT_EXISTS(0x6),
    NOT_RING_MEMBER(0x7),
    NOT_ENOUGH_SIGNATURES(0x8),
    INVALID_SIGNATURE_BOUNDS(0x9),
    INVALID_TRANSFER(0xA),
    NOT_RELAYER(0xB),
    UNCAUGHT_ERROR(0x1337);

    private final int errCode;

    private ErrCode(int i) {
        this.errCode = i;
    }
}
