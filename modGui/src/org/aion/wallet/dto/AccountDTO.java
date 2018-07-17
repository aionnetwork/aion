package org.aion.wallet.dto;

import org.aion.base.util.TypeConverter;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.util.QRCodeUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class AccountDTO {

    private final String currency;
    private final String publicAddress;
    private final boolean isImported;
    private final int derivationIndex;
    private final BufferedImage qrCode;
    private final SortedSet<TransactionDTO> transactions = new TreeSet<>();
    private final List<SendTransactionDTO> timedOutTransactions = new ArrayList<>();
    private byte[] privateKey;
    private String balance;  //TODO this has to be BigInteger
    private String name;
    private boolean active;
    private BlockDTO lastSafeBlock = null;

    public AccountDTO(final String name, final String publicAddress, final String balance, final String currency, boolean isImported, int derivationIndex) {
        this.name = name;
        this.publicAddress = TypeConverter.toJsonHex(publicAddress);
        this.balance = balance;
        this.currency = currency;
        this.qrCode = QRCodeUtils.writeQRCode(publicAddress);
        this.isImported = isImported;
        this.derivationIndex = derivationIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isImported() {
        return isImported;
    }

    public int getDerivationIndex() {
        return derivationIndex;
    }

    public BufferedImage getQrCode() {
        return qrCode;
    }

    public SortedSet<TransactionDTO> getTransactionsSnapshot() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(transactions));
    }

    public void addTransactions(final Collection<TransactionDTO> transactions) {
        this.transactions.addAll(transactions);
    }

    public void removeTransactions(final Collection<TransactionDTO> transactions) {
        this.transactions.removeAll(transactions);
    }

    public BlockDTO getLastSafeBlock() {
        return lastSafeBlock;
    }

    public void setLastSafeBlock(final BlockDTO lastSafeBlock) {
        this.lastSafeBlock = lastSafeBlock;
    }

    public List<SendTransactionDTO> getTimedOutTransactions() {
        return timedOutTransactions;
    }

    public void addTimedOutTransaction(SendTransactionDTO transaction) {
        if (transaction == null) {
            return;
        }
        this.timedOutTransactions.add(transaction);
    }

    public void removeTimedOutTransaction(SendTransactionDTO transaction) {
        if (transaction == null) {
            return;
        }
        this.timedOutTransactions.remove(transaction);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AccountDTO that = (AccountDTO) o;
        return Objects.equals(currency, that.currency) &&
                Objects.equals(publicAddress, that.publicAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, publicAddress);
    }

    public boolean isUnlocked() {
        return privateKey != null;
    }

}
