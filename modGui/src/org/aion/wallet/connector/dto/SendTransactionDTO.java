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

import java.math.BigInteger;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.util.AddressUtils;

public class SendTransactionDTO {

    private String from;
    private String password = "";
    private String to;
    private Long nrg;
    private BigInteger nrgPrice;
    private BigInteger value;
    private byte[] data = ByteArrayWrapper.NULL_BYTE;
    private BigInteger nonce = BigInteger.ZERO;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Long getNrg() {
        return nrg;
    }

    public void setNrg(Long nrg) {
        this.nrg = nrg;
    }

    public Long getNrgPrice() {
        return nrgPrice.longValue();
    }

    public void setNrgPrice(BigInteger nrgPrice) {
        this.nrgPrice = nrgPrice;
    }

    public BigInteger getValue() {
        return value;
    }

    public void setValue(BigInteger value) {
        this.value = value;
    }

    public BigInteger estimateValue() {
        return value.add(nrgPrice.multiply(BigInteger.valueOf(nrg)));
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public BigInteger getNonce() {
        return nonce;
    }

    private void setNonce(final BigInteger nonce) {
        this.nonce = nonce;
    }

    public boolean validate() throws ValidationException {
        if (!AddressUtils.isValid(from)) {
            throw new ValidationException("Invalid from address");
        }
        if (!AddressUtils.isValid(to)) {
            throw new ValidationException("Invalid to address");
        }
        if (value == null || value.compareTo(BigInteger.ZERO) <= 0) {
            throw new ValidationException("A value greater than zero must be provided");
        }
        if (nrg == null || nrg < 0) {
            throw new ValidationException("Invalid nrg value");
        }
        if (nrgPrice == null || nrgPrice.longValue() < 0) {
            throw new ValidationException("Invalid nrg price");
        }

        // it's always embedded currently, so not doing this for now
//        if(ConfigUtils.isEmbedded() && (password == null || password.equals(""))) {
//            throw new ValidationException("Password is invalid");
//        }

        return true;
    }
}
