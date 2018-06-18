package org.aion.precompiled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DataWord;
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
    private List<Address> addrsToClean;

    @Before
    public void setup() {
        repo = new DummyRepo();
        ((DummyRepo) repo).storageErrorReturn = null;
        addrsToClean = new ArrayList<>();
    }

    @After
    public void tearDown() {
        for (Address addr : addrsToClean) {
            repo.deleteAccount(addr);
        }
        repo = null;
        addrsToClean = null;
    }

    // Creates a new account with initial balance balance that will be deleted at test end.
    private Address getExistentAddress(BigInteger balance) {
        Address addr = Address.wrap(ECKeyFac.inst().create().getAddress());
        repo.createAccount(addr);
        repo.addBalance(addr, balance);
        addrsToClean.add(addr);
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

    // Returns a list of existent accounts of size umOtherOwners + 1 that contains owner and then
    // numOtherOwners other owners each with initial balance balance.
    private List<Address> getExistentAddresses(int numOtherOwners, Address owner, BigInteger balance) {
        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < numOtherOwners; i++) {
            accounts.add(getExistentAddress(balance));
        }
        accounts.add(owner);
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

    // Returns a list of size 2 containing the threshold & number of owners of this multi-sig wallet.
    // If walletId is not a multi-sig wallet this method fails.
    private List<Long> getWalletThresholdAndNumOwners(Address walletId) {
        List<Long> values = new ArrayList<>();
        byte[] metaKey = new byte[DataWord.BYTES];
        metaKey[0] = (byte) 0x80;
        DataWord metaData = (DataWord) repo.getStorageValue(walletId, new DataWord(metaKey));
        if (metaData == null) { fail(); }
        byte[] rawData = metaData.getData();
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(rawData, 0, Long.BYTES));
        buffer.flip();
        values.add(buffer.getLong());
        buffer.flip();
        buffer.put(Arrays.copyOfRange(rawData, Long.BYTES, DataWord.BYTES));
        buffer.flip();
        values.add(buffer.getLong());
        return values;
    }

    // Returns a set of numOwners owners of the wallet. If the wallet has duplicate owners this
    // method fails.
    private Set<Address> getWalletOwners(Address walletId, long numOwners) {
        Set<Address> owners = new HashSet<>();
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        DataWord portion;

        for (long i = 0; i < numOwners; i++) {
            byte[] account = new byte[Address.ADDRESS_LEN];
            buffer.putLong(i);
            buffer.flip();
            byte[] request = new byte[DataWord.BYTES];
            buffer.get(request, DataWord.BYTES - Long.BYTES, Long.BYTES);
            portion = (DataWord) repo.getStorageValue(walletId, new DataWord(request));
            if (portion == null) { fail(); }
            System.arraycopy(portion.getData(), 0, account, 0, DataWord.BYTES);

            request[0] = (byte) 0x40;
            portion = (DataWord) repo.getStorageValue(walletId, new DataWord(request));
            if (portion == null) { fail(); }
            System.arraycopy(portion.getData(), 0, account, DataWord.BYTES, DataWord.BYTES);

            Address address = new Address(account);
            if (owners.contains(address)) { fail(); }
            owners.add(address);
            buffer.flip();
            buffer.clear();
        }
        return owners;
    }

    // <------------------------------------MISCELLANEOUS TESTS------------------------------------>

    @Test(expected=IllegalArgumentException.class)
    public void testConstructWithNullTrack() {
        new MultiSignatureContract(null, Address.wrap(ECKeyFac.inst().create().getAddress()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructWithNullCaller() {
        new MultiSignatureContract(repo, null);
    }

    @Test
    public void testNrgLessThanCost() {
        // Test with min illegal cost.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, Long.MIN_VALUE);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());

        // Test with max illegal cost.
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
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);
        input[0] = (byte) 0x2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    // <------------------------------------CREATE WALLET TESTS------------------------------------>

    @Test
    public void testCreateWalletThresholdBelowLegalLimit() {
        // Test with min illegal value.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(Long.MIN_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with max illegal value.
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH - 1, owners);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithThresholdLargerThanNumOwners() {
        // Test with max illegal value.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = toValidCreateInput(Long.MAX_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with smallest illegal value.
        input = toValidCreateInput(owners.size() + 1, owners);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletZeroOwners() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = new ArrayList<>();
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletOneOwner() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(1, BigInteger.ZERO);
        byte[] input = toValidCreateInput(Long.MAX_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithMoreOwnersThanLegalLimit() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(MultiSignatureContract.MAX_OWNERS + 1,
            BigInteger.ZERO);
        byte[] input = toValidCreateInput(Long.MAX_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithTwoDuplicateOwners() {
        // Test with max amount of owners
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(MultiSignatureContract.MAX_OWNERS - 1,
            BigInteger.ZERO);
        owners.add(owners.get(0));
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with min amount of owners
        owners = getExistentAddresses(MultiSignatureContract.MIN_OWNERS - 1,
            BigInteger.ZERO);
        owners.add(owners.get(0));
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test all owners same
        owners.clear();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS; i++) {
            owners.add(caller);
        }
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletButCallerIsNotAnOwner() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(MultiSignatureContract.MIN_OWNERS, BigInteger.ZERO);
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    // Caller is not in repo <-- that is meaning of 'non-existent'
    @Test
    public void testCreateWalletWithNonExistentCaller() {
        // Test using min legal number of owners.
        Address caller = Address.wrap(ECKeyFac.inst().create().getAddress());
        List<Address> owners = getExistentAddresses(
            MultiSignatureContract.MIN_OWNERS - 1, caller, BigInteger.ZERO);
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test using max legal number of owners.
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 1, caller, BigInteger.ZERO);
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithNonExistentOwnerThatIsNotCaller() {
        // Test using min legal number of owners.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = new ArrayList<>();
        owners.add(caller);
        owners.add(Address.wrap(ECKeyFac.inst().create().getAddress()));
        for (int i = 0; i < (MultiSignatureContract.MIN_OWNERS - 2); i++) {
            owners.add(getExistentAddress(BigInteger.ZERO));
        }
        byte[] input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test using max legal number of owners.
        for (int i = MultiSignatureContract.MIN_OWNERS; i < MultiSignatureContract.MAX_OWNERS; i++) {
            owners.add(getExistentAddress(BigInteger.ZERO));
        }
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithPartiallyCompleteAddress() {
        // Test on nearly min legal number of owners.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(MultiSignatureContract.MIN_OWNERS - 1,
            BigInteger.ZERO);
        owners.add(caller);
        byte[] in = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        byte[] partialAddr = new byte[Address.ADDRESS_LEN - 1];
        ThreadLocalRandom.current().nextBytes(partialAddr);

        byte[] input = new byte[in.length + partialAddr.length];
        System.arraycopy(in, 0, input, 0, in.length);
        System.arraycopy(partialAddr, 0, input, in.length, partialAddr.length);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test on max legal number of owners.
        owners = getExistentAddresses(MultiSignatureContract.MAX_OWNERS - 2,
            BigInteger.ZERO);
        in = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        input = new byte[in.length + partialAddr.length];
        System.arraycopy(in, 0, input, 0, in.length);
        System.arraycopy(partialAddr, 0, input, in.length, partialAddr.length);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithMultiSigWalletCaller() {
        // First create a multi-sig wallet.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(
            MultiSignatureContract.MIN_OWNERS - 1, caller, BigInteger.ZERO);
        long threshold = owners.size();
        byte[] input = toValidCreateInput(threshold, owners);
        long nrg = COST * 2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        Address walletCaller = new Address(res.getOutput());
        addrsToClean.add(walletCaller);

        // Now try to create a wallet using this wallet as the caller and an owner.
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 1, walletCaller, BigInteger.ZERO);
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, walletCaller);
        res = msc.execute(input, nrg);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateWalletWithOwnerAsAMultiSigWallet() {
        // First create a multi-sig wallet.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(
            MultiSignatureContract.MIN_OWNERS - 1, caller, BigInteger.ZERO);
        long threshold = owners.size();
        byte[] input = toValidCreateInput(threshold, owners);
        long nrg = COST * 2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        Address wallet = new Address(res.getOutput());
        addrsToClean.add(wallet);

        // Now try to create a wallet using this wallet as one of the owners.
        Address newCaller = getExistentAddress(BigInteger.ZERO);
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 2, wallet, BigInteger.ZERO);
        owners.add(newCaller);
        input = toValidCreateInput(MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, newCaller);
        res = msc.execute(input, nrg);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateWalletWithThresholdEqualToLegalNumOwners() {
        // Test using min legal number of owners.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(
            MultiSignatureContract.MIN_OWNERS - 1, caller, BigInteger.ZERO);
        long threshold = owners.size();
        byte[] input = toValidCreateInput(threshold, owners);
        long nrg = COST * 2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        // check the wallet is in proper initial state.
        Address walletId = new Address(res.getOutput());
        addrsToClean.add(walletId);
        assertEquals(BigInteger.ZERO, repo.getBalance(walletId));
        assertEquals(BigInteger.ZERO, repo.getNonce(walletId));

        List<Long> threshAndOwners = getWalletThresholdAndNumOwners(walletId);
        assertEquals(threshold, threshAndOwners.get(0).longValue());
        assertEquals(owners.size(), threshAndOwners.get(1).longValue());

        Set<Address> walletOwners = getWalletOwners(walletId, threshAndOwners.get(1));
        assertEquals(owners.size(), walletOwners.size());
        for (Address own : owners) {
            assertTrue(walletOwners.contains(own));
        }

        // Test using max legal number of owners.
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 1, caller, BigInteger.ZERO);
        threshold = owners.size();
        input = toValidCreateInput(threshold, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        // check the wallet is in proper initial state.
        walletId = new Address(res.getOutput());
        addrsToClean.add(walletId);
        assertEquals(BigInteger.ZERO, repo.getBalance(walletId));
        assertEquals(BigInteger.ZERO, repo.getNonce(walletId));

        threshAndOwners = getWalletThresholdAndNumOwners(walletId);
        assertEquals(threshold, threshAndOwners.get(0).longValue());
        assertEquals(owners.size(), threshAndOwners.get(1).longValue());

        walletOwners = getWalletOwners(walletId, threshAndOwners.get(1));
        assertEquals(owners.size(), walletOwners.size());
        for (Address own : owners) {
            assertTrue(walletOwners.contains(own));
        }
    }

    @Test
    public void testCreateWalletWithMinimumLegalThreshold() {
        // Test using min legal number of owners.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(
            MultiSignatureContract.MIN_OWNERS - 1, caller, BigInteger.ZERO);
        long threshold = MultiSignatureContract.MIN_THRESH;
        byte[] input = toValidCreateInput(threshold, owners);
        long nrg = COST * 2;

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        // check the wallet is in proper initial state.
        Address walletId = new Address(res.getOutput());
        addrsToClean.add(walletId);
        assertEquals(BigInteger.ZERO, repo.getBalance(walletId));
        assertEquals(BigInteger.ZERO, repo.getNonce(walletId));

        List<Long> threshAndOwners = getWalletThresholdAndNumOwners(walletId);
        assertEquals(threshold, threshAndOwners.get(0).longValue());
        assertEquals(owners.size(), threshAndOwners.get(1).longValue());

        Set<Address> walletOwners = getWalletOwners(walletId, threshAndOwners.get(1));
        assertEquals(owners.size(), walletOwners.size());
        for (Address own : owners) {
            assertTrue(walletOwners.contains(own));
        }

        // Test using max legal number of owners.
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 1, caller, BigInteger.ZERO);
        threshold = owners.size();
        input = toValidCreateInput(threshold, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, nrg);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrg - COST, res.getNrgLeft());

        // check the wallet is in proper initial state.
        walletId = new Address(res.getOutput());
        addrsToClean.add(walletId);
        assertEquals(BigInteger.ZERO, repo.getBalance(walletId));
        assertEquals(BigInteger.ZERO, repo.getNonce(walletId));

        threshAndOwners = getWalletThresholdAndNumOwners(walletId);
        assertEquals(threshold, threshAndOwners.get(0).longValue());
        assertEquals(owners.size(), threshAndOwners.get(1).longValue());

        walletOwners = getWalletOwners(walletId, threshAndOwners.get(1));
        assertEquals(owners.size(), walletOwners.size());
        for (Address own : owners) {
            assertTrue(walletOwners.contains(own));
        }
    }

}
