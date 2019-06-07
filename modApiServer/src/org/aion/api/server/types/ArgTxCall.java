package org.aion.api.server.types;

import static org.aion.mcf.vm.Constants.NRG_CREATE_CONTRACT_DEFAULT;
import static org.aion.mcf.vm.Constants.NRG_TRANSACTION_DEFAULT;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.util.types.AddressUtils;
import org.json.JSONObject;
import org.slf4j.Logger;

/** @author chris */
public final class ArgTxCall {

    private final AionAddress from;
    private final AionAddress to;
    private final byte[] data;
    private final byte type;
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
        this(_from, _to, _data, _nonce, _value, _nrg, _nrgPrice, TransactionTypes.DEFAULT);
    }

    public ArgTxCall(
            final AionAddress _from,
            final AionAddress _to,
            final byte[] _data,
            final BigInteger _nonce,
            final BigInteger _value,
            final long _nrg,
            final long _nrgPrice,
            final byte _type) {
        this.from = _from;
        this.to = _to;
        this.data = _data == null ? ByteUtil.EMPTY_BYTE_ARRAY : _data;
        this.nonce = _nonce == null ? BigInteger.ZERO : _nonce;
        this.value = _value == null ? BigInteger.ZERO : _value;
        this.nrg = _nrg;
        this.nrgPrice = _nrgPrice;
        this.type = _type;
    }

    public static ArgTxCall fromJSON(final JSONObject _jsonObj, long defaultNrgPrice) {
        return fromJSON(_jsonObj, defaultNrgPrice, false);
    }

    public static ArgTxCall fromJSONforCall(final JSONObject _jsonObj, long defaultNrgPrice) {
        return fromJSON(_jsonObj, defaultNrgPrice, true);
    }

    private static ArgTxCall fromJSON(
        final JSONObject _jsonObj, long defaultNrgPrice, boolean forCall) {
        try {

            String fromStr = _jsonObj.optString("from", "");
            AionAddress from =
                    fromStr.equals("") ? null : new AionAddress(ByteUtil.hexStringToBytes(fromStr));

            String toStr = _jsonObj.optString("to", "");
            AionAddress to = toStr.equals("") ? null : AddressUtils.wrapAddress(toStr);

            byte[] data = ByteUtil.hexStringToBytes(_jsonObj.optString("data", ""));
            byte type = ByteUtil.hexStringToBytes(_jsonObj.optString("type", "0x1"))[0];

            String nonceStr = _jsonObj.optString("nonce", "0x0");
            String valueStr = _jsonObj.optString("value", "0x0");
            BigInteger nonce =
                    nonceStr.contains("0x")
                            ? StringUtils.StringHexToBigInteger(nonceStr)
                            : StringUtils.StringNumberAsBigInt(nonceStr);
            BigInteger value =
                    valueStr.contains("0x")
                            ? StringUtils.StringHexToBigInteger(valueStr)
                            : StringUtils.StringNumberAsBigInt(valueStr);

            String nrgStr = _jsonObj.optString("gas", null);
            String nrgPriceStr = _jsonObj.optString("gasPrice", null);

            long nrg;
            if (nrgStr != null) {
                nrg =
                        nrgStr.contains("0x")
                                ? StringUtils.StringHexToBigInteger(nrgStr).longValue()
                                : StringUtils.StringNumberAsBigInt(nrgStr).longValue();
            } else {
                if (forCall) {
                    nrg = Long.MAX_VALUE;
                } else {
                    nrg = to == null ? NRG_CREATE_CONTRACT_DEFAULT : NRG_TRANSACTION_DEFAULT;
                }
            }

            long nrgPrice = defaultNrgPrice;
            if (nrgPriceStr != null)
                nrgPrice =
                        nrgPriceStr.contains("0x")
                                ? StringUtils.StringHexToBigInteger(nrgPriceStr).longValue()
                                : StringUtils.StringNumberAsBigInt(nrgPriceStr).longValue();

            return new ArgTxCall(from, to, data, nonce, value, nrg, nrgPrice, type);
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

    public byte getType() {
        return type;
    }
}
