package org.aion.txpool.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

public class TxDependList {
    private final List<ByteArrayWrapper> txList;
    private ByteArrayWrapper dependTx;
    private AionAddress address;
    private BigInteger timeStamp;

    public TxDependList() {
        txList = new ArrayList<>();
    }

    public AionAddress getAddress() {
        return address;
    }

    public void setAddress(AionAddress address) {
        this.address = address;
    }

    public ByteArrayWrapper getDependTx() {
        return dependTx;
    }

    public void setDependTx(ByteArrayWrapper tx) {
        this.dependTx = tx;
    }

    public List<ByteArrayWrapper> getTxList() {
        return txList;
    }

    public void addTx(ByteArrayWrapper tx) {
        txList.add(tx);
    }

    public void addTxAll(List<ByteArrayWrapper> txl) {
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

    public int compare(TxDependList td) {
        return timeStamp.compareTo(td.timeStamp);
    }
}
