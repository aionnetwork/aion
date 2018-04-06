/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.txpool.common;

import org.aion.base.type.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
