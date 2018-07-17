package org.aion.wallet.connector.dto;

import org.aion.base.type.Hash256;

public class TransactionResponseDTO {
    private final byte status;
    private final Hash256 txHash;
    private final String error;

    public TransactionResponseDTO() {
        status = 0;
        txHash = null;
        error = null;
    }

    public TransactionResponseDTO(final byte status, final Hash256 txHash, final String error){
        this.status = status;
        this.txHash = txHash;
        this.error = error;
    }

    public byte getStatus() {
        return status;
    }

    public Hash256 getTxHash() {
        return txHash;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "TransactionResponseDTO{" +
                "status=" + status +
                ", txHash=" + txHash +
                ", error='" + error + '\'' +
                '}';
    }
}
