package org.aion.zero.impl;

import java.math.BigInteger;
import java.util.*;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.types.AionTransaction;

/**
 * Methods used by several classes testing the blockchain functionality.
 *
 * @author Alexandra Roatis
 */
public class BlockchainTestUtils {
    private static Random rand = new Random();
    private static final byte[] ZERO_BYTE = new byte[0];
    private static final long NRG = 21000L;
    private static final long NRG_PRICE = 1L;

    public static List<ECKey> generateAccounts(int size) {
        List<ECKey> accs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            accs.add(ECKeyFac.inst().create());
        }
        return accs;
    }

    public static List<AionTransaction> generateTransactions(
            int maxSize, List<ECKey> accounts, AionRepositoryImpl repo) {
        int size = rand.nextInt(maxSize);

        if (size == 0) {
            return Collections.emptyList();
        } else {
            // get the current nonce for each account
            Map<ECKey, BigInteger> nonces = new HashMap<>();
            for (ECKey key : accounts) {
                nonces.put(key, repo.getNonce(new Address(key.getAddress())));
            }

            List<AionTransaction> transactions = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                // get a random account
                ECKey key = accounts.get(rand.nextInt(accounts.size()));
                BigInteger accountNonce = nonces.get(key);

                // generate a random address
                Address destAddr = new Address(HashUtil.h256(accountNonce.toByteArray()));
                AionTransaction newTx =
                        new AionTransaction(
                                accountNonce.toByteArray(),
                                destAddr,
                                BigInteger.ONE.toByteArray(),
                                ZERO_BYTE,
                                NRG,
                                NRG_PRICE);
                newTx.sign(key);
                transactions.add(newTx);
                accountNonce = accountNonce.add(BigInteger.ONE);
                nonces.put(key, accountNonce);
            }

            return transactions;
        }
    }
}
