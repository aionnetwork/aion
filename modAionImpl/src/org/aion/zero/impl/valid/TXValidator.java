package org.aion.zero.impl.valid;

import static org.aion.zero.impl.vm.common.TxNrgRule.isValidNrgContractCreate;
import static org.aion.zero.impl.vm.common.TxNrgRule.isValidNrgContractCreateAfterUnity;
import static org.aion.zero.impl.vm.common.TxNrgRule.isValidNrgTx;
import static org.aion.zero.impl.vm.common.TxNrgRule.isValidNrgTxAfterUnity;

import java.util.Collections;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.LogEnum;
import org.aion.util.types.DataWord;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.util.types.Hash256;
import org.aion.zero.impl.types.TxResponse;
import org.aion.zero.impl.vm.common.TxNrgRule;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TXValidator {

    private static final Logger LOG = LoggerFactory.getLogger(LogEnum.TX.name());

    private static final Map<ByteArrayWrapper, TxResponse> cache =
            Collections.synchronizedMap(new LRUMap<>(16 * 1024));

    public static TxResponse validateTx(AionTransaction tx, boolean unityForkEnabled) {
        TxResponse valid = cache.get(ByteArrayWrapper.wrap(tx.getTransactionHash()));
        if (valid != null) {
            return valid;
        } else {
            if (unityForkEnabled) {
                valid = isValidAfterUnity(tx);
            } else {
                valid = isValid0(tx);
            }
            cache.put(ByteArrayWrapper.wrap(tx.getTransactionHash()), valid);
            return valid;
        }
    }

    public static boolean isInCache(ByteArrayWrapper hash) {
        return cache.get(hash) != null;
    }

    private static TxResponse isValid0(AionTransaction tx) {
        long nrg = tx.getEnergyLimit();
        if (tx.isContractCreationTransaction()) {
            if (!isValidNrgContractCreate(nrg)) {
                LOG.error("invalid contract create nrg!");
                return TxResponse.INVALID_TX_NRG_LIMIT;
            }
        } else {
            if (!isValidNrgTx(nrg)) {
                LOG.error("invalid tx nrg!");
                return TxResponse.INVALID_TX_NRG_LIMIT;
            }
        }

        return isValidInner(tx);
    }

    private static TxResponse isValidAfterUnity(AionTransaction tx) {
        long nrg = tx.getEnergyLimit();
        if (tx.isContractCreationTransaction()) {
            if (!isValidNrgContractCreateAfterUnity(nrg, tx.getData())) {
                LOG.error("invalid contract create nrg!");
                return TxResponse.INVALID_TX_NRG_LIMIT;
            }
        } else {
            if (!isValidNrgTxAfterUnity(nrg, tx.getData())) {
                LOG.error("invalid tx nrg!");
                return TxResponse.INVALID_TX_NRG_LIMIT;
            }
        }

        return isValidInner(tx);
    }

    private static TxResponse isValidInner(AionTransaction tx) {
        byte[] check = tx.getNonce();
        if (check == null || check.length > DataWord.BYTES) {
            LOG.error("invalid tx nonce!");
            return TxResponse.INVALID_TX_NONCE;
        }

        check = tx.getTimestamp();
        if (check == null || check.length > Long.BYTES) {
            LOG.error("invalid tx timestamp!");
            return TxResponse.INVALID_TX_TIMESTAMP;
        }

        check = tx.getValue();
        if (check == null || check.length > DataWord.BYTES) {
            LOG.error("invalid tx value!");
            return TxResponse.INVALID_TX_VALUE;
        }

        check = tx.getData();
        if (check == null) {
            LOG.error("invalid tx data!");
            return TxResponse.INVALID_TX_DATA;
        }

        if (!TxNrgRule.isValidTxNrgPrice(tx.getEnergyPrice())) {
            LOG.error("invalid tx nrg price!");
            return TxResponse.INVALID_TX_NRG_PRICE;
        }

        byte[] hash = tx.getTransactionHashWithoutSignature();
        if (hash == null || hash.length != Hash256.BYTES) {
            LOG.error("invalid tx raw hash!");
            return TxResponse.INVALID_TX_HASH;
        }

        ISignature sig = tx.getSignature();
        if (sig == null) {
            LOG.error("invalid tx signature!");
            return TxResponse.INVALID_TX_SIGNATURE;
        }

        try {
            return SignatureFac.verify(hash, sig) ? TxResponse.SUCCESS : TxResponse.INVALID_TX_SIGNATURE;
        } catch (Exception ex) {
            ex.printStackTrace();
            return TxResponse.INVALID_TX_SIGNATURE;
        }
    }
}
