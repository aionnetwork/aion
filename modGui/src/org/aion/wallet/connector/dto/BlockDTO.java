package org.aion.wallet.connector.dto;

public class BlockDTO {
    private final long number;
    private final byte[] hash;

    public BlockDTO(final long number, final byte[] hash) {
        this.number = number;
        this.hash = hash;
    }

    public long getNumber() {
        return number;
    }

    public byte[] getHash() {
        return hash;
    }
}
