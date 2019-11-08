package org.aion.avm.version2.contracts.exchange_on_chain;

import avm.Address;
import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeObjects.AccountData;
import org.aion.avm.version2.contracts.exchange_on_chain.OnChainTokenExchangeObjects.TokenUtility;

public class OnChainTokenExchangeStorage {

    // used for deriving storage key
    private enum StorageSlots {
        TOKEN,
        ACCOUNT,
        BALANCE,
        ORDER
    }

    protected static void putToken(Address tokenAddress, TokenUtility details) {
        byte[] key = getKey(StorageSlots.TOKEN, tokenAddress.toByteArray());
        byte[] value = details == null ? null : details.toBytes();
        Blockchain.putStorage(key, value);
    }

    protected static void removeToken(Address tokenAddress) {
        byte[] key = getKey(StorageSlots.TOKEN, tokenAddress.toByteArray());
        Blockchain.putStorage(key, null);
    }

    protected static TokenUtility getToken(Address tokenAddress) {
        byte[] key = getKey(StorageSlots.TOKEN, tokenAddress.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : TokenUtility.fromBytes(value);
    }

    protected static void putAccount(Address account, AccountData accountData) {
        byte[] key = getKey(StorageSlots.ACCOUNT, account.toByteArray());
        byte[] value = accountData == null ? null : accountData.toBytes();
        Blockchain.putStorage(key, value);
    }

    protected static AccountData getAccount(Address account) {
        byte[] key = getKey(StorageSlots.ACCOUNT, account.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : AccountData.fromBytes(value);
    }

    protected static void putBalance(Address account, Address token, BigInteger amountOwned) {
        byte[] key = getKey(StorageSlots.BALANCE, account.toByteArray(), token.toByteArray());
        byte[] value = amountOwned == null ? null : amountOwned.toByteArray();
        Blockchain.putStorage(key, value);
    }

    protected static BigInteger getBalance(Address account, Address token) {
        byte[] key = getKey(StorageSlots.BALANCE, account.toByteArray(), token.toByteArray());
        byte[] value = Blockchain.getStorage(key);
        return value == null ? null : new BigInteger(value);
    }

    private static byte[] getKey(Enum storageSlot, byte[] key) {
        int outputSize = Integer.BYTES + key.length;
        AionBuffer buffer = AionBuffer.allocate(outputSize);
        buffer.putInt(storageSlot.hashCode());
        buffer.put(key);

        return Blockchain.blake2b(buffer.getArray());
    }

    private static byte[] getKey(Enum storageSlot, byte[] key1, byte[] key2) {
        int outputSize = Integer.BYTES + key1.length + key2.length;
        AionBuffer buffer = AionBuffer.allocate(outputSize);
        buffer.putInt(storageSlot.hashCode());
        buffer.put(key1);
        buffer.put(key2);

        return Blockchain.blake2b(buffer.getArray());
    }
}
