package org.aion.precompiled;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.MultiSignatureContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the MultiSignatureContract API.
 */
public class MultiSignatureContractTest {
    private static final long COST = 21000L; // must be equal to valid used in contract.

    private IRepositoryCache repo;
    private List<Address> tempAddrs;

    @Before
    public void setup() {
        repo = new DummyRepo();
        tempAddrs = new ArrayList<>();
    }

    @After
    public void tearDown() {
        for (Address addr : tempAddrs) {
            repo.deleteAccount(addr);
        }
        repo = null;
        tempAddrs = null;
    }

    // Creates a new account with initial balance balance that will be deleted at test end.
    private Address getExistentAddress(BigInteger balance) {
        Address addr = Address.wrap(ECKeyFac.inst().create().getAddress());
        repo.createAccount(addr);
        repo.addBalance(addr, balance);
        tempAddrs.add(addr);
        return addr;
    }

    // Returns a list of existent accounts of size numOwners, each of which has initial balance.
    private List<Address> getExistentAddresses(int numOwners, BigInteger balance) {
        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < numOwners; i++) {
            accounts.add(getExistentAddress(balance));
        }
        return accounts;
    }

    // Returns a properly formatted byte array for these input params for create-wallet logic.
    private byte[] toValidCreateInput(long threshold, List<Address> owners) {
        int len = 1 + Long.BYTES + (owners.size() * 32);
        byte[] input = new byte[len];

        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(new byte[]{ (byte) 0x0 });
        buffer.putLong(threshold);
        for (Address addr : owners) {
            buffer.put(addr.toBytes());
        }

        buffer.flip();
        buffer.get(input);
        return input;
    }

    // <------------------------------------MISCELLANEOUS TESTS------------------------------------>

    @Test
    public void testNrgLessThanCost() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(2, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, Long.MIN_VALUE);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());

        res = msc.execute(input, COST - 1);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());
    }

    @Test
    public void testNullInput() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(null, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testEmptyInput() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testInputWithOperationOnly() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(new byte[] { (byte) 0x0 }, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    // improve?
    @Test
    public void testInputWithUnsupportedOperation() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(2, owners);
        input[0] = (byte) 0x2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    // <------------------------------------CREATE WALLET TESTS------------------------------------>

    @Test
    public void testCreateWalletThresholdBelowLegalLimit() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(Long.MIN_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH - 1, owners);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletThresholdAboveLegalLimit() {

    }

    @Test
    public void testCreateWalletZeroOwners() {

    }

    @Test
    public void testCreateWalletOneOwner() {

    }

    @Test
    public void testCreateWalletWithMoreOwnersThanLegalLimit() {

    }

    @Test
    public void testCreateWalletWithThresholdLargerThanNumOwners() {

    }

    @Test
    public void testCreateWalletWithTwoDuplicateOwners() {

    }

    @Test
    public void testCreateWalletWithNonExistentOwner() {

    }

    @Test
    public void testCreateWalletWithNullCaller() {

    }

    @Test
    public void testCreateWalletWithEmptyAddrCaller() {

    }

    @Test
    public void testCreateWalletWithNonExistentCaller() {

    }

    @Test
    public void testCreateWalletWithMultiSigWalletCaller() {

    }

    @Test
    public void testCreateWalletWithOwnerAsAMultiSigWallet() {
        // don't want this.
    }

    @Test
    public void testCreateWalletWithPartiallyCompleteAddress() {

    }

    @Test
    public void testCreateWalletWithThresholdEqualToNumOwners() {

    }

    @Test
    public void testCreateWalletWithMinimumAllowableOwners() {

    }

    @Test
    public void testCreateWalletWithMaximumAllowableOwners() {

    }

}
