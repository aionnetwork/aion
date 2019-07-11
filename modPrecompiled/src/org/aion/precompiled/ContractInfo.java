package org.aion.precompiled;

import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

/**
 * Contains basic information relating to a precompiled contract. Namely, the address of that
 * contract and the address of the account that owns the contract, if the contract has an owner.
 */
public enum ContractInfo {
    TOTAL_CURRENCY(
            "0000000000000000000000000000000000000000000000000000000000000100",
            "0000000000000000000000000000000000000000000000000000000000000000"),
    TOKEN_BRIDGE(
            "0000000000000000000000000000000000000000000000000000000000000200",
            "a008d7b29e8d1f4bfab428adce89dc219c4714b2c6bf3fd1131b688f9ad804aa"),
    ED_VERIFY("0000000000000000000000000000000000000000000000000000000000000010", null),
    BLAKE_2B("0000000000000000000000000000000000000000000000000000000000000011", null),
    TRANSACTION_HASH("0000000000000000000000000000000000000000000000000000000000000012", null);

    public final AionAddress contractAddress;
    public final AionAddress ownerAddress;

    ContractInfo(String address, String owner) {
        this.contractAddress = new AionAddress(ByteUtil.hexStringToBytes(address));
        this.ownerAddress =
                (owner == null) ? null : new AionAddress(ByteUtil.hexStringToBytes(owner));
    }

    /**
     * Returns true if address is the address of a pre-compiled contract and false otherwise.
     *
     * @param address The address to check.
     * @return true iff address is address of a pre-compiled contract.
     */
    public static boolean isPrecompiledContract(AionAddress address) {
        for (ContractInfo contractInfo : ContractInfo.values()) {
            if ((contractInfo != ContractInfo.TOTAL_CURRENCY)
                    && (address.equals(contractInfo.contractAddress))) {
                return true;
            }
        }
        return false;
    }
}
