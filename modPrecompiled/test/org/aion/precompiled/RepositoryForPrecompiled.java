package org.aion.precompiled;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

public class RepositoryForPrecompiled {
    private Map<AionAddress, AccountState> repo = new HashMap<>();

    public void addStorageRow(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value) {
        AccountState state = repo.get(address);
        if (state == null) {
            state = new AccountState();
            repo.put(address, state);
        }
        state.storage.put(key, value);
    }

    public void removeStorageRow(AionAddress address, ByteArrayWrapper key) {
        AccountState state = repo.get(address);
        if (state == null) return;
        state.storage.remove(key);
    }

    public ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key) {
        AccountState state = repo.get(address);
        return state == null ? null : state.storage.get(key);
    }

    public BigInteger getBalance(AionAddress address) {
        AccountState state = repo.get(address);
        return state == null ? BigInteger.ZERO : state.balance;
    }

    public void addBalance(AionAddress address, BigInteger amount) {
        AccountState state = repo.get(address);
        if (state == null) {
            state = new AccountState();
            state.balance = state.balance.add(amount);
            repo.put(address, state);
        } else {
            state.balance = state.balance.add(amount);
        }
    }

    public BigInteger getNonce(AionAddress address) {
        AccountState state = repo.get(address);
        return state == null ? BigInteger.ZERO : state.nonce;
    }

    public void incrementNonce(AionAddress address) {
        AccountState state = repo.get(address);
        if (state == null) {
            state = new AccountState();
            repo.put(address, state);
        }
        state.nonce = state.nonce.add(BigInteger.ONE);
    }

    private class AccountState {
        BigInteger nonce = BigInteger.ZERO;
        BigInteger balance = BigInteger.ZERO;
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
    }
}
