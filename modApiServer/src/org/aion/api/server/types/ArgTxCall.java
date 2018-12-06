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
package org.aion.api.server.types;

import java.math.BigInteger;
import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.json.JSONObject;
import org.slf4j.Logger;

/** @author chris */
public final class ArgTxCall {

    private final AionAddress from;
    private final AionAddress to;
    private final byte[] data;
    private final BigInteger nonce;
    private final BigInteger value;
    private final long nrg;
    private final long nrgPrice;

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    // @Jay
    // TODO: create a builder class for create this class

    public ArgTxCall(
            final AionAddress _from,
            final AionAddress _to,
            final byte[] _data,
            final BigInteger _nonce,
            final BigInteger _value,
            final long _nrg,
            final long _nrgPrice) {
        this.from = _from;
        this.to = _to;
        this.data = _data == null ? ByteUtil.EMPTY_BYTE_ARRAY : _data;
        this.nonce = _nonce == null ? BigInteger.ZERO : _nonce;
        this.value = _value == null ? BigInteger.ZERO : _value;
        this.nrg = _nrg;
        this.nrgPrice = _nrgPrice;
    }

    public static ArgTxCall fromJSON(
            final JSONObject _jsonObj, NrgOracle oracle, long defaultNrgLimit) {
        try {
            AionAddress from = AionAddress
                .wrap(ByteUtil.hexStringToBytes(_jsonObj.optString("from", "")));
            AionAddress to = AionAddress.wrap(ByteUtil.hexStringToBytes(_jsonObj.optString("to", "")));
            byte[] data = ByteUtil.hexStringToBytes(_jsonObj.optString("data", ""));

            String nonceStr = _jsonObj.optString("nonce", "0x0");
            String valueStr = _jsonObj.optString("value", "0x0");
            BigInteger nonce =
                    nonceStr.contains("0x")
                            ? TypeConverter.StringHexToBigInteger(nonceStr)
                            : TypeConverter.StringNumberAsBigInt(nonceStr);
            BigInteger value =
                    valueStr.contains("0x")
                            ? TypeConverter.StringHexToBigInteger(valueStr)
                            : TypeConverter.StringNumberAsBigInt(valueStr);

            String nrgStr = _jsonObj.optString("gas", null);
            String nrgPriceStr = _jsonObj.optString("gasPrice", null);

            long nrg = defaultNrgLimit;
            if (nrgStr != null)
                nrg =
                        nrgStr.contains("0x")
                                ? TypeConverter.StringHexToBigInteger(nrgStr).longValue()
                                : TypeConverter.StringNumberAsBigInt(nrgStr).longValue();

            long nrgPrice;
            if (nrgPriceStr != null)
                nrgPrice =
                    nrgPriceStr.contains("0x")
                                ? TypeConverter.StringHexToBigInteger(nrgPriceStr).longValue()
                                : TypeConverter.StringNumberAsBigInt(nrgPriceStr).longValue();
            else nrgPrice = oracle.getNrgPrice();

            return new ArgTxCall(from, to, data, nonce, value, nrg, nrgPrice);
        } catch (Exception e) {
            LOG.debug("Failed to parse transaction call object from input parameters", e);
            return null;
        }
    }

    public AionAddress getFrom() {
        return this.from;
    }

    public AionAddress getTo() {
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
