package org.aion.api.server.rpc2.autogen.pod;

/******************************************************************************
 *
 * AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
 * BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
 *
 *****************************************************************************/
public class Transaction {
    private byte[] blockHash;
    private java.math.BigInteger blockNumber;
    private byte[] from;
    private java.math.BigInteger nrg;
    private java.math.BigInteger nrgPrice;
    private java.math.BigInteger gas;
    private java.math.BigInteger gasPrice;
    private byte[] hash;
    private byte[] input;
    private java.math.BigInteger nonce;
    private byte[] to;
    private java.math.BigInteger transactionIndex;
    private java.math.BigInteger value;
    private java.math.BigInteger timestamp;

    public Transaction(
        byte[] blockHash,
        java.math.BigInteger blockNumber,
        byte[] from,
        java.math.BigInteger nrg,
        java.math.BigInteger nrgPrice,
        java.math.BigInteger gas,
        java.math.BigInteger gasPrice,
        byte[] hash,
        byte[] input,
        java.math.BigInteger nonce,
        byte[] to,
        java.math.BigInteger transactionIndex,
        java.math.BigInteger value,
        java.math.BigInteger timestamp
    ) {
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.from = from;
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.hash = hash;
        this.input = input;
        this.nonce = nonce;
        this.to = to;
        this.transactionIndex = transactionIndex;
        this.value = value;
        this.timestamp = timestamp;
    }

    public byte[] getBlockHash() {
        return this.blockHash;
    }

    public java.math.BigInteger getBlockNumber() {
        return this.blockNumber;
    }

    public byte[] getFrom() {
        return this.from;
    }

    public java.math.BigInteger getNrg() {
        return this.nrg;
    }

    public java.math.BigInteger getNrgPrice() {
        return this.nrgPrice;
    }

    public java.math.BigInteger getGas() {
        return this.gas;
    }

    public java.math.BigInteger getGasPrice() {
        return this.gasPrice;
    }

    public byte[] getHash() {
        return this.hash;
    }

    public byte[] getInput() {
        return this.input;
    }

    public java.math.BigInteger getNonce() {
        return this.nonce;
    }

    public byte[] getTo() {
        return this.to;
    }

    public java.math.BigInteger getTransactionIndex() {
        return this.transactionIndex;
    }

    public java.math.BigInteger getValue() {
        return this.value;
    }

    public java.math.BigInteger getTimestamp() {
        return this.timestamp;
    }

}
