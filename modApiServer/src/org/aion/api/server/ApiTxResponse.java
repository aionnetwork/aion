/*
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
 */

package org.aion.api.server;

import org.aion.mcf.blockchain.TxResponse;
import org.aion.vm.api.interfaces.Address;

public class ApiTxResponse {

    private final TxResponse rsp;

    private byte[] txHash;

    private Address contractAddress;

    // Could just store the exception message string
    private Exception ex;

    ApiTxResponse(TxResponse rsp) {
        this.rsp = rsp;
    }

    ApiTxResponse(TxResponse rsp, byte[] txHash) {
        this.rsp = rsp;
        this.txHash = txHash;
    }

    ApiTxResponse(TxResponse rsp, byte[] txHash, Address contractAddress) {
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
            case REPAYTX_POOL_EXCEPTION:
                return "Repaid transaction wasn't found in the pool";
            case REPAYTX_LOWPRICE:
                return "Repaid transaction needs to have a higher energy price";
            case DROPPED:
                return "Transaction dropped";
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

    public Address getContractAddress() {
        return contractAddress;
    }
}
