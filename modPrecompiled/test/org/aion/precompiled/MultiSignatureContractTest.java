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
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.MultiSignatureContract;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the MultiSignatureContract API.
 */
public class MultiSignatureContractTest {
    private static final long COST = 21000L; // must be equal to valid used in contract.
    private static final int SIG_SIZE = 96; // must be equal to valid used in contract.
    private static final int AMT_SIZE = 128; // must be equal to valid used in contract.
    private static final BigInteger defaultBalance = new BigInteger("100000");
    private IRepositoryCache repo;
    private List<Address> addrsToClean;

    @Before
    public void setup() {
        CfgAion.inst();
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

    // <--------------------------------------HELPER METHODS--------------------------------------->

    // Creates a new account with initial balance balance that will be deleted at test end.
    private Address getExistentAddress(BigInteger balance) {
        Address addr = Address.wrap(ECKeyFac.inst().create().getAddress());
        repo.createAccount(addr);
        repo.addBalance(addr, balance);
        addrsToClean.add(addr);
        return addr;
    }

    // Returns a list of existent accounts of size numOwners, each of which has initial balance.
    private List<Address> getExistentAddresses(long numOwners, BigInteger balance) {
        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < numOwners; i++) {
            accounts.add(getExistentAddress(balance));
        }
        return accounts;
    }

    // Returns a list of existent accounts of size umOtherOwners + 1 that contains owner and then
    // numOtherOwners other owners each with initial balance balance.
    private List<Address> getExistentAddresses(long numOtherOwners, Address owner, BigInteger balance) {
        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < numOtherOwners; i++) {
            accounts.add(getExistentAddress(balance));
        }
        accounts.add(owner);
        return accounts;
    }

    // This is the constructMsg method provided by MSC class but here you can specify your nonce.
    public static byte[] customMsg(IRepositoryCache repo, BigInteger nonce, Address walletId,
        Address to, BigInteger amount, long nrgPrice, long nrgLimit) {

        byte[] nonceBytes = nonce.toByteArray();
        byte[] toBytes = to.toBytes();
        byte[] amountBytes = amount.toByteArray();
        int len = nonceBytes.length + toBytes.length + amountBytes.length + (Long.BYTES * 2);

        byte[] msg = new byte[len];
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(nonceBytes);
        buffer.put(toBytes);
        buffer.put(amountBytes);
        buffer.putLong(nrgLimit);
        buffer.putLong(nrgPrice);
        buffer.flip();
        buffer.get(msg);
        return msg;
    }

    // Returns a properly formatted byte array for these input params for send-tx logic. We want to
    // allow null values so we can simulate missing elements in the input array.
    private byte[] toValidSendInput(Address wallet, List<ISignature> signatures, BigInteger amount,
        long nrgPrice, Address to) {

        int walletLen = (wallet == null) ? 0 : Address.ADDRESS_LEN;
        int sigsLen = (signatures == null) ? 0 : (signatures.size() * SIG_SIZE);
        int amtLen = (amount == null) ? 0 : AMT_SIZE;
        int toLen = (to == null) ? 0 : Address.ADDRESS_LEN;

        int len = 1 + walletLen + sigsLen + amtLen + Long.BYTES + toLen;
        byte[] input = new byte[len];

        int index = 0;
        input[index] = (byte) 0x1;
        index++;
        if (wallet != null) {
            System.arraycopy(wallet.toBytes(), 0, input, index, Address.ADDRESS_LEN);
            index += Address.ADDRESS_LEN;
        }

        if (signatures != null) {
            for (ISignature sig : signatures) {
                byte[] sigBytes = sig.toBytes();
                System.arraycopy(sigBytes, 0, input, index, sigBytes.length);
                index += sigBytes.length;
            }
        }

        if (amount != null) {
            byte[] amt = new byte[AMT_SIZE];
            byte[] amtBytes = amount.toByteArray();
            System.arraycopy(amtBytes, 0, amt, AMT_SIZE - amtBytes.length, amtBytes.length);
            System.arraycopy(amt, 0, input, index, AMT_SIZE);
            index += AMT_SIZE;
        }

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(nrgPrice);
        buffer.flip();
        byte[] nrg = new byte[Long.BYTES];
        buffer.get(nrg);
        System.arraycopy(nrg, 0, input, index, Long.BYTES);
        index += Long.BYTES;

        if (to != null) {
            System.arraycopy(to.toBytes(), 0, input, index, Address.ADDRESS_LEN);
        }
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

    // Returns a list of numKeys keys.
    private List<ECKeyEd25519> produceKeys(long numKeys) {
        List<ECKeyEd25519> keys = new ArrayList<>();
        for (long i = 0; i < numKeys; i++) {
            keys.add(new ECKeyEd25519());
        }
        return keys;
    }

    // Returns the address of the new multi-sig wallet that uses the addresses in the keys of owners
    // as the owners, requires at least threshold signatures and has initial balance balance.
    private Address createMultiSigWallet(List<ECKeyEd25519> owners, long threshold, BigInteger balance) {
        if (owners.isEmpty()) { fail(); }
        List<Address> ownerAddrs = new ArrayList<>();
        Address addr;
        for (ECKeyEd25519 key : owners) {
            addr = new Address(key.getAddress());
            repo.createAccount(addr);
            addrsToClean.add(addr);
            ownerAddrs.add(addr);
        }

        byte[] input = MultiSignatureContract.constructCreateWalletInput(threshold, ownerAddrs);
        MultiSignatureContract msc = new MultiSignatureContract(repo, ownerAddrs.get(0));
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        Address wallet = new Address(res.getOutput());
        repo.addBalance(wallet, balance);
        addrsToClean.add(wallet);
        repo.flush();
        return wallet;
    }

    // Shifts all bytes at index and to the right of index left by 1 position, losing a single byte.
    private byte[] shiftLeftAtIndex(byte[] original, int index) {
        byte[] shifted = new byte[original.length - 1];
        System.arraycopy(original, 0, shifted, 0, index - 1);
        System.arraycopy(original, index, shifted, index - 1, original.length - index);
        return shifted;
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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

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
        ContractExecutionResult res = msc.execute(new byte[]{ (byte) 0x0 }, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        res = msc.execute(new byte[]{ (byte) 0x1 }, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testInputWithUnsupportedOperation() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);
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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(Long.MIN_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with max illegal value.
        input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH - 1, owners);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletWithThresholdLargerThanNumOwners() {
        // Test with max illegal value.
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(3, BigInteger.ZERO);
        byte[] input = MultiSignatureContract.constructCreateWalletInput(Long.MAX_VALUE, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with smallest illegal value.
        input = MultiSignatureContract.constructCreateWalletInput(owners.size() + 1, owners);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletZeroOwners() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = new ArrayList<>();
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletOneOwner() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(1, BigInteger.ZERO);
        byte[] input = MultiSignatureContract.constructCreateWalletInput(Long.MAX_VALUE, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(Long.MAX_VALUE, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test with min amount of owners
        owners = getExistentAddresses(MultiSignatureContract.MIN_OWNERS - 1,
            BigInteger.ZERO);
        owners.add(owners.get(0));
        input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test all owners same
        owners.clear();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS; i++) {
            owners.add(caller);
        }
        input = MultiSignatureContract.constructCreateWalletInput(MultiSignatureContract.MIN_THRESH,
            owners);

        msc = new MultiSignatureContract(repo, caller);
        res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
    }

    @Test
    public void testCreateWalletButCallerIsNotAnOwner() {
        Address caller = getExistentAddress(BigInteger.ZERO);
        List<Address> owners = getExistentAddresses(MultiSignatureContract.MIN_OWNERS, BigInteger.ZERO);
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test using max legal number of owners.
        owners = getExistentAddresses(
            MultiSignatureContract.MAX_OWNERS - 1, caller, BigInteger.ZERO);
        input = MultiSignatureContract.constructCreateWalletInput(MultiSignatureContract.MIN_THRESH,
            owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, COST);
        assertEquals(0, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());

        // Test using max legal number of owners.
        for (int i = MultiSignatureContract.MIN_OWNERS; i < MultiSignatureContract.MAX_OWNERS; i++) {
            owners.add(getExistentAddress(BigInteger.ZERO));
        }
        input = MultiSignatureContract.constructCreateWalletInput(MultiSignatureContract.MIN_THRESH,
            owners);

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
        byte[] in = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

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
        in = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);
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
        input = MultiSignatureContract.constructCreateWalletInput(
            MultiSignatureContract.MIN_THRESH, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);
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
        input = MultiSignatureContract.constructCreateWalletInput(MultiSignatureContract.MIN_THRESH, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);
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
        input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);

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
        byte[] input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);
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
        input = MultiSignatureContract.constructCreateWalletInput(threshold, owners);

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

    // <----------------------------------SEND TRANSACTION TESTS----------------------------------->

    @Test
    public void testSendTxWithZeroSignatures() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // No signatures.
        List<ISignature> signatures = new ArrayList<>();

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWithMoreThanMaxOwnersSignatures() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS + 1);
        ECKeyEd25519 extra = owners.remove(0);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MAX_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have all owners plus one extra sign the tx.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }
        signatures.add(extra.sign(txMsg));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxValidSignaturesMeetsThresholdPlusPhonySig() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS + 1);
        ECKeyEd25519 phony = owners.remove(0);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have all owners sign, meet threshold requirement, and attach a phony signaure.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }
        signatures.add(phony.sign(txMsg));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNegativeAmountWithZeroBalance() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, BigInteger.ZERO);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN.negate();
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNegativeAmountWithActualBalance() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN.negate();
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxFromRegularAddress() {
        // Our wallet is not a wallet...
        List<ECKeyEd25519> phonies = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address phonyWallet = new Address(phonies.get(0).getAddress());

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.ONE;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, phonyWallet, to, amount, nrgPrice,
            nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 phony : phonies) {
            signatures.add(phony.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(phonyWallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, phonyWallet);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNoSenderInInput() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // input does not contain the sender info.
        byte[] input = toValidSendInput(null, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNoRecipient() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // input does not contain the recipient info.
        byte[] input = toValidSendInput(wallet, signatures, amount, nrgPrice, null);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNoAmount() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // input does not contain the amount info.
        byte[] input = toValidSendInput(wallet, signatures, null, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxNoNrgPrice() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // input does not contain the amount info.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        byte[] noNrgInput = new byte[input.length - Long.BYTES];
        System.arraycopy(input, 0, noNrgInput, 0,
            input.length - Address.ADDRESS_LEN - Long.BYTES - 1);
        System.arraycopy(input, input.length - Address.ADDRESS_LEN, noNrgInput,
            input.length - Address.ADDRESS_LEN - Long.BYTES, Address.ADDRESS_LEN);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(noNrgInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWithSignatureUsingPreviousNonce() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // We sign a tx msg that uses the previous nonce.
        BigInteger nonce = repo.getNonce(wallet);
        byte[] txMsg = customMsg(repo, nonce.subtract(BigInteger.ONE), wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWhereSignedMessagesDifferInNonce() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // One signee signs tx with a different nonce than the others. The others sign correct tx.
        BigInteger nonce = repo.getNonce(wallet);
        byte[] correctTx = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        byte[] badNonceTx = customMsg(repo, nonce.subtract(BigInteger.ONE), wallet, to, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(correctTx));
        }
        signatures.add(owners.get(MultiSignatureContract.MIN_OWNERS - 1).sign(badNonceTx));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWhereSignedMessagesDifferInRecipient() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // One signee signs tx with a different recipient than the others. The others sign correct tx.
        byte[] correctTx = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        byte[] badToTx = MultiSignatureContract.constructMsg(repo, wallet, caller, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(correctTx));
        }
        signatures.add(owners.get(MultiSignatureContract.MIN_OWNERS - 1).sign(badToTx));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWhereSignedMessagesDifferInAmount() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // One signee signs tx with a different amount than the others. The others sign correct tx.
        byte[] correctTx = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        byte[] badAmtTx = MultiSignatureContract.constructMsg(repo, wallet, caller,
            amount.subtract(BigInteger.ONE), nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(correctTx));
        }
        signatures.add(owners.get(MultiSignatureContract.MIN_OWNERS - 1).sign(badAmtTx));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWhereSignedMessagesDifferInNrgLimit() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // One signee signs tx with a different nrg limit than the others. The others sign correct tx.
        byte[] correctTx = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        byte[] badLimTx = MultiSignatureContract.constructMsg(repo, wallet, caller, amount, nrgPrice, nrgLimit - 1);

        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(correctTx));
        }
        signatures.add(owners.get(MultiSignatureContract.MIN_OWNERS - 1).sign(badLimTx));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxWhereSignedMessagesDifferInNrgPrice() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // One signee signs tx with a different nrg price than the others. The others sign correct tx.
        byte[] correctTx = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        byte[] badNrgTx = MultiSignatureContract.constructMsg(repo, wallet, caller, amount, nrgPrice - 1, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(correctTx));
        }
        signatures.add(owners.get(MultiSignatureContract.MIN_OWNERS - 1).sign(badNrgTx));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxAllSignWrongRecipient() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        Address stranger = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Everyone signs a valid recipient and whole tx is fine but the recipient stated in input differs.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // The recipient in input differs from all the signatures.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice,
            stranger);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxAllSignWrongAmount() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Everyone signs a valid amount and whole tx is fine but the amount stated in input differs.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // The recipient in input differs from all the signatures.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures,
            amount.subtract(BigInteger.ONE), nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxAllSignWrongNonce() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        Address stranger = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Everyone signs a different nonce than the wallet's current one.
        BigInteger nonce = repo.getNonce(wallet);
        byte[] txMsg = customMsg(repo, nonce.add(BigInteger.ONE), wallet, to, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // The recipient in input differs from all the signatures.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice,
            stranger);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxAllSignWrongNrgLimit() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Everyone signs a valid nrgLimit and whole tx is fine but the limit stated in execute differs.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // The nrgLimit given to execute method differs from all the signatures.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxAllSignWrongNrgPrice() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Everyone signs a valid nrgPrice and whole tx is fine but the nrgPrice stated in input differs.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice + 1, nrgLimit);

        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        // The recipient in input differs from all the signatures.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxRecipientDoesNotExist() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        // Recipient is not in db.
        Address to = Address.wrap(ECKeyFac.inst().create().getAddress());
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxInsufficientBalance() {
        // Create account with zero balance.
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, BigInteger.ZERO);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testWalletAbleToSendTxToDiffWallet() {
        List<ECKeyEd25519> owners1 = produceKeys(MultiSignatureContract.MIN_OWNERS);
        List<ECKeyEd25519> owners2 = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners1.get(0).getAddress());
        Address wallet1 = createMultiSigWallet(owners1, MultiSignatureContract.MIN_THRESH, defaultBalance);
        Address wallet2 = createMultiSigWallet(owners2, MultiSignatureContract.MIN_THRESH, defaultBalance);

        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Sign tx to send from wallet1 to wallet2.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet1, wallet2, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners1.get(i).sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet1, signatures, amount, nrgPrice,
            wallet2);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    @Test
    public void testSendTxLessSignaturesThanThresholdMinOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have 1 less owner than required sign the tx msg.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxLessSignaturesThanThresholdMaxOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MAX_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have 1 less owner than required sign the tx msg.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MAX_OWNERS - 1; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxSameSignaturesAsThresholdMinOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have each owner sign the tx msg.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    @Test
    public void testSendTxSameSignaturesAsThresholdMaxOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MAX_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have each owner sign the tx msg.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    @Test
    public void testSendTxMoreSignaturesThanThresholdMinOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS - 1, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have all the owners sign.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    @Test
    public void testSendTxMoreSignaturesThanThresholdMaxOwners() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Have all the owners sign.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (ECKeyEd25519 owner : owners) {
            signatures.add(owner.sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    @Test
    public void testSendTxDuplicateSignee() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MAX_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // All owners but 1 sign, and 1 signs twice to meet threshold req.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MAX_OWNERS; i++) {
            if (i == MultiSignatureContract.MAX_OWNERS - 1) {
                signatures.add(owners.get(i - 1).sign(txMsg));
            } else {
                signatures.add(owners.get(i).sign(txMsg));
            }
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testSendTxSignatureOneSigneeIsNonOwner() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        ECKeyEd25519 phony = produceKeys(1).get(0);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MAX_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // All owners but 1 sign, and then the phony signs.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MAX_OWNERS - 1; i++) {
                signatures.add(owners.get(i).sign(txMsg));
        }
        signatures.add(phony.sign(txMsg));

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    // This is ok: caller is owner and so their absent sig does not matter if all sigs are ok.
    @Test
    public void testSendTxSignedProperlyButNotSignedByOwnerCaller() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MAX_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_THRESH, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Adequate number of signees but we skip signee 0 since they are caller.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 1; i <= MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(nrgLimit - COST, res.getNrgLeft());
    }

    // This is bad: only want transactions triggered by a calling owner, even if caller doesn't sign.
    @Test
    public void testSendTxSignedProperlyButCallerIsNotOwner() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        ECKeyEd25519 phony = produceKeys(1).get(0);
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // The phony is the one who calls the contract though.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        MultiSignatureContract msc = new MultiSignatureContract(repo, new Address(phony.getAddress()));
        ContractExecutionResult res = msc.execute(input, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPartialSignature() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // Input gets shifted to lose 1 byte of the last signature.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        int amtStart = input.length - Address.ADDRESS_LEN - Long.BYTES - AMT_SIZE;
        byte[] shiftedInput = shiftLeftAtIndex(input, amtStart);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(shiftedInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPartialWalletAddress() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // Input gets shifted to lose 1 byte of the wallet address.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        int sigsStart = 1 + Address.ADDRESS_LEN;
        byte[] shiftedInput = shiftLeftAtIndex(input, sigsStart);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(shiftedInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPartialRecipientAddress() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // Input gets shifted to lose 1 byte of the recipient address.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        int end = input.length;
        byte[] shiftedInput = shiftLeftAtIndex(input, end);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(shiftedInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPartialAmount() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // Input gets shifted to lose 1 byte of the amount.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        int nrgStart = input.length - Address.ADDRESS_LEN - Long.BYTES;
        byte[] shiftedInput = shiftLeftAtIndex(input, nrgStart);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(shiftedInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPartialNrgPrice() {
        List<ECKeyEd25519> owners = produceKeys(MultiSignatureContract.MIN_OWNERS);
        Address caller = new Address(owners.get(0).getAddress());
        Address wallet = createMultiSigWallet(owners, MultiSignatureContract.MIN_OWNERS, defaultBalance);

        Address to = getExistentAddress(BigInteger.ZERO);
        BigInteger amount = BigInteger.TEN;
        long nrgLimit = 100000L;
        long nrgPrice = 10000000000L;

        // Signed adequately.
        byte[] txMsg = MultiSignatureContract.constructMsg(repo, wallet, to, amount, nrgPrice, nrgLimit);
        List<ISignature> signatures = new ArrayList<>();
        for (int i = 0; i < MultiSignatureContract.MIN_THRESH; i++) {
            signatures.add(owners.get(i).sign(txMsg));
        }

        // Input gets shifted to lose 1 byte of the energy price.
        byte[] input = MultiSignatureContract.constructSendTxInput(wallet, signatures, amount, nrgPrice, to);
        int toStart = input.length - Address.ADDRESS_LEN;
        byte[] shiftedInput = shiftLeftAtIndex(input, toStart);

        MultiSignatureContract msc = new MultiSignatureContract(repo, caller);
        ContractExecutionResult res = msc.execute(shiftedInput, nrgLimit);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

}
