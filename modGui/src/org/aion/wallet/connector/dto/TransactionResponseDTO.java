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
