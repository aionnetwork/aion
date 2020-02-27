package org.aion.api.server;

import org.aion.zero.impl.types.TxResponse;
import org.aion.types.AionAddress;

public class ApiTxResponse {

    private final TxResponse rsp;

    private byte[] txHash;

    private AionAddress contractAddress;

    // Could just store the exception message string
    private Exception ex;

    ApiTxResponse(TxResponse rsp) {
        this.rsp = rsp;
    }

    ApiTxResponse(TxResponse rsp, byte[] txHash) {
        this.rsp = rsp;
        this.txHash = txHash;
    }

    ApiTxResponse(TxResponse rsp, byte[] txHash, AionAddress contractAddress) {
        this.rsp = rsp;
        this.txHash = txHash;
        this.contractAddress = contractAddress;
    }

    ApiTxResponse(TxResponse rsp, Exception ex) {
        this.rsp = rsp;
        this.ex = ex;
    }

    public TxResponse getType() {
        return rsp;
    }

    public String getMessage() {
        if (rsp.equals(TxResponse.EXCEPTION)) {
            return ex.getMessage();
        } else {
            return rsp.getMessage();
        }
    }

    public boolean isFail() {
        return rsp.isFail();
    }

    // Should only be called if tx was successfully sent
    public byte[] getTxHash() {
        return txHash;
    }

    public AionAddress getContractAddress() {
        return contractAddress;
    }
}
