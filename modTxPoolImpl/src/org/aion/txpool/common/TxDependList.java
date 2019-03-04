package org.aion.txpool.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.aion.types.Address;

public class TxDependList<BW> {
    private final List<BW> txList;
    private BW dependTx;
    private Address address;
    private BigInteger timeStamp;

    public TxDependList() {
        txList = new ArrayList<>();
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public BW getDependTx() {
        return dependTx;
    }

    public void setDependTx(BW tx) {
        this.dependTx = tx;
    }

    public List<BW> getTxList() {
        return txList;
    }

    public void addTx(BW tx) {
        txList.add(tx);
    }

    public void addTxAll(List<BW> txl) {
        txList.addAll(txl);
    }

    public boolean isEmpty() {
        return txList.isEmpty();
    }

    public BigInteger getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(BigInteger timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int compare(TxDependList<BW> td) {
        return timeStamp.compareTo(td.timeStamp);
    }
}
