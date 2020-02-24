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
        switch (rsp) {
            case SUCCESS:
                return "Transaction sent successfully";
            case INVALID_TX:
                return "Invalid transaction object";
            case INVALID_TX_NRG_PRICE:
                return "Invalid transaction energy price";
            case INVALID_TX_NRG_LIMIT:
                return "Invalid transaction energy limit";
            case INVALID_TX_NONCE:
                return "Invalid transaction nonce";
            case INVALID_TX_TIMESTAMP:
                return "Invalid transaction timestamp";
            case INVALID_TX_VALUE:
                return "Invalid transaction value";
            case INVALID_TX_DATA:
                return "Invalid transaction data";
            case INVALID_TX_HASH:
                return "Invalid transaction hash";
            case INVALID_TX_SIGNATURE:
                return "Invalid transaction signature";
            case INVALID_TX_TYPE:
                return "Invalid transaction type";
            case INVALID_TX_BEACONHASH:
                return "Invalid transaction beacon hash";
            case INVALID_FROM:
                return "Invalid from address provided";
            case INVALID_ACCOUNT:
                return "Account not found, or not unlocked";
            case ALREADY_CACHED:
                return "Transaction is already in the cache";
            case CACHED_NONCE:
                return "Transaction cached due to large nonce";
            case CACHED_POOLMAX:
                return "Transaction cached because the pool is full";
            case REPAID:
                return "Transaction successfully repaid";
            case ALREADY_SEALED:
                return "Transaction has already been sealed in the repo";
            case REPAYTX_BUFFER_FULL:
                return "Repaid transaction dropped due to the buffer full";
            case REPAYTX_LOWPRICE:
                return "Repaid transaction needs to have a higher energy price";
            case DROPPED:
                return "Transaction dropped";
            case CACHED_TIMEOUT:
                return "Transaction dropped due to the transaction cache timeout";
            case TXPOOL_TIMEOUT:
                return "Transaction dropped due to the transaction pool timeout";
            case CACHED_ACCOUNTMAX:
                return "Transaction dropped due to the cache account reach max";
            case EXCEPTION:
                return ex.getMessage();
            default:
                return "Transaction status unknown";
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
