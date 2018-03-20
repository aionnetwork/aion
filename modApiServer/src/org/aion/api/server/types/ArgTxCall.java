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

package org.aion.api.server.types;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.json.JSONObject;

import java.math.BigInteger;

/**
 * @author chris
 */

public final class ArgTxCall {

    private final Address from;
    private final Address to;
    private final byte[] data;
    private final BigInteger nonce;
    private final BigInteger value;
    private final long nrg;
    private final long nrgPrice;

    // @Jay
    //TODO: create a builder class for create this class

    public ArgTxCall(final Address _from, final Address _to, final byte[] _data, final BigInteger _nonce, final BigInteger _value, final long _nrg, final long _nrgPrice) {
        this.from = _from;
        this.to = _to;
        this.data = _data == null ? ByteUtil.EMPTY_BYTE_ARRAY : _data;
        this.nonce = _nonce == null ? BigInteger.ZERO : _nonce;
        this.value = _value == null ? BigInteger.ZERO : _value;
        this.nrg = _nrg;
        this.nrgPrice = _nrgPrice;
    }

    public static ArgTxCall fromJSON(final JSONObject _jsonObj, long nrgRecommended, long defaultNrgLimit){
        try {
            Address from = Address.wrap(ByteUtil.hexStringToBytes(_jsonObj.optString("from", "")));
            Address to = Address.wrap(ByteUtil.hexStringToBytes(_jsonObj.optString("to", "")));
            byte[] data = ByteUtil.hexStringToBytes(_jsonObj.optString("data", ""));

            String nonceStr = _jsonObj.optString("nonce", "0x0");
            String valueStr = _jsonObj.optString("value", "0x0");
            BigInteger nonce = nonceStr.indexOf("0x") >= 0 ? TypeConverter.StringHexToBigInteger(nonceStr) : TypeConverter.StringNumberAsBigInt(nonceStr);
            BigInteger value = valueStr.indexOf("0x") >= 0 ? TypeConverter.StringHexToBigInteger(valueStr) : TypeConverter.StringNumberAsBigInt(valueStr);

            String nrgStr = _jsonObj.optString("gas", null);
            String nrgPriceStr = _jsonObj.optString("gasPrice", null);

            long nrg = defaultNrgLimit;
            if (nrgStr != null)
                nrg = nrgStr.indexOf("0x") >= 0 ? TypeConverter.StringHexToBigInteger(nrgStr).longValue() : TypeConverter.StringNumberAsBigInt(nrgStr).longValue();

            long nrgPrice = nrgRecommended;
            if (nrgPriceStr != null)
                nrgPrice = nrgPriceStr.indexOf("0x") >=0 ? TypeConverter.StringHexToBigInteger(nrgPriceStr).longValue() : TypeConverter.StringNumberAsBigInt(nrgPriceStr).longValue();

            return new ArgTxCall(from, to, data, nonce, value, nrg, nrgPrice);
        } catch(Exception ex) {
            return null;
        }
    }
    
    public Address getFrom() {
        return this.from;
    }

    public Address getTo() {
        return this.to;
    }

    public byte[] getData() {
        return this.data;
    }

    public BigInteger getNonce() {
        return this.nonce;
    }

    public BigInteger getValue() {
        return this.value;
    }

    public long getNrg() {
        return nrg;
    }

    public long getNrgPrice() {
        return nrgPrice;
    }

}
