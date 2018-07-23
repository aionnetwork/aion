package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteArrayWrapper;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static org.aion.precompiled.contracts.ATB.BridgeUtilities.toSignature;

enum BridgeFuncSig {
    SIG_CHANGE_OWNER        ("changeOwner(address)"),
    SIG_ACCEPT_OWNERSHIP    ("acceptOwnership()"),
    SIG_RING_INITIALIZE     ("initializeRing(address[])"),
    SIG_RING_ADD_MEMBER     ("addRingMember(address)"),
    SIG_RING_REMOVE_MEMBER  ("removeRingMember(address)"),
    SIG_SET_RELAYER         ("setRelayer(address)"),
    SIG_SUBMIT_BUNDLE       ("submitBundle(bytes32,address[],uint128[],bytes32[],bytes32[],bytes32)"),
    PURE_OWNER              ("owner()"),
    PURE_NEW_OWNER          ("newOwner()"),
    PURE_ACTION_MAP         ("actionMap(bytes32)"),
    PURE_RING_MAP           ("ringMap(address)"),
    PURE_RING_LOCKED        ("ringLocked()"),
    PURE_MIN_THRESH         ("minThresh()"),
    PURE_MEMBER_COUNT       ("memberCount()"),
    PURE_RELAYER            ("relayer()");

    private static Map<ByteArrayWrapper, BridgeFuncSig> enumSet = new HashMap<>();

    static {
        for (BridgeFuncSig v : BridgeFuncSig.values()) {
            enumSet.put(v.sigWrapper, v);
        }
    }

    private ByteArrayWrapper sigWrapper;
    private String signature;

    BridgeFuncSig(@Nonnull final String signature) {
        this.signature = signature;
        this.sigWrapper = new ByteArrayWrapper(toSignature(signature));
    }

    /**
     * Maintains a 1-1 mapping between the function signature and the enum representation.
     *
     * @param arr 4-byte array input
     * @return {@code enum} representing the function signature to be switched upon, if
     * the input is considered malformed, or the signature is missing this function will
     * return {@code null}
     */
    public static BridgeFuncSig getSignatureEnum(@Nonnull final byte[] arr) {
        if (arr.length != 4)
            return null;

        return enumSet.get(new ByteArrayWrapper(arr));
    }

    public byte[] getBytes() {
        return this.sigWrapper.toBytes();
    }

    public String getSignature() {
        return this.signature;
    }
}
