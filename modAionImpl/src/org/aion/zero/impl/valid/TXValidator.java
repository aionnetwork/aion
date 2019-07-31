package org.aion.zero.impl.valid;

import static org.aion.mcf.valid.TxNrgRule.isValidNrgContractCreate;
import static org.aion.mcf.valid.TxNrgRule.isValidNrgTx;

import java.util.Collections;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.util.types.Hash256;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TXValidator {

    private static final Logger LOG = LoggerFactory.getLogger(LogEnum.TX.name());

    private static final Map<ByteArrayWrapper, Boolean> cache =
            Collections.synchronizedMap(new LRUMap<>(16 * 1024));

    public static boolean isValid(AionTransaction tx) {
        Boolean valid = cache.get(ByteArrayWrapper.wrap(tx.getTransactionHash()));
        if (valid != null) {
            return valid;
        } else {
            valid = isValid0(tx);
            cache.put(ByteArrayWrapper.wrap(tx.getTransactionHash()), valid);
            return valid;
        }
    }

    public static boolean isInCache(ByteArrayWrapper hash) {
        return cache.get(hash) != null;
    }

    public static boolean isValid0(AionTransaction tx) {
        byte[] check = tx.getNonce();
        if (check == null || check.length > DataWordImpl.BYTES) {
            LOG.error("invalid tx nonce!");
            return false;
        }

        check = tx.getTimestamp();
        if (check == null || check.length > Long.BYTES) {
            LOG.error("invalid tx timestamp!");
            return false;
        }

        check = tx.getValue();
        if (check == null || check.length > DataWordImpl.BYTES) {
            LOG.error("invalid tx value!");
            return false;
        }

        check = tx.getData();
        if (check == null) {
            LOG.error("invalid tx data!");
            return false;
        }

        long nrg = tx.getEnergyLimit();
        if (tx.isContractCreationTransaction()) {
            if (!isValidNrgContractCreate(nrg)) {
                LOG.error("invalid contract create nrg!");
                return false;
            }
        } else {
            if (!isValidNrgTx(nrg)) {
                LOG.error("invalid tx nrg!");
                return false;
            }
        }

        if (tx.getEnergyPrice() < 0) {
            LOG.error("invalid tx nrgprice!");
            return false;
        }

        byte[] hash = tx.getTransactionHashWithoutSignature();
        if (hash == null || hash.length != Hash256.BYTES) {
            LOG.error("invalid tx raw hash!");
            return false;
        }

        ISignature sig = tx.getSignature();
        if (sig == null) {
            LOG.error("invalid tx signature!");
            return false;
        }

        try {
            return SignatureFac.verify(hash, sig);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
