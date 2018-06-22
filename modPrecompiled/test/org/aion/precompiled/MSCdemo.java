package org.aion.precompiled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.api.type.MsgRsp;
import org.aion.api.type.TxArgs;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.contracts.MultiSignatureContract;

public class MSCdemo {
    private static final Address CONTRACT = ContractFactory.getMultiSignatureContractAddress();
    private static final String URL = IAionAPI.LOCALHOST_URL;
    private static final String PW = "test";

    private static void withApi() {
        IAionAPI api = IAionAPI.init();

        ApiMsg apiMsg = api.connect(URL);
        assertFalse(apiMsg.isError());

        // Step 1. Create a new multi-sig wallet with 3 owners, requiring 2 signatures.
        Address member1 = new Address("0xa04c7d16d52a9bb666ebb4c4e2fe68683c0b84b75b26f43c10a47ee739476ab9");
        Address member2 = new Address("0xa06eadbf1b71784afc15727e4526f484c06adda06b0f6a46aa8fdf40710ab646");
        Address member3 = new Address("0xa0e8b0b7017afd443072b65b106742737b68bc26a58ef24dbc94902de523a8bd");
        List<Address> accs = api.getWallet().getAccounts().getObject();
        Address to = accs.get(ThreadLocalRandom.current().nextInt(accs.size()));

        assertNotEquals(member1, member2);
        List<Address> owners = new ArrayList<>();
        owners.add(member1);
        owners.add(member2);
        owners.add(member3);

        assertTrue(api.getWallet().unlockAccount(member1, PW).getObject());

        System.out.println("[1] Creating the new multi-sig wallet...");
        byte[] create = MultiSignatureContract.constructCreateWalletInput(2, owners);
        TxArgs.TxArgsBuilder builder = new TxArgs.TxArgsBuilder()
            .data(new ByteArrayWrapper(create))
            .from(member1)
            .to(CONTRACT)
            .nrgLimit(100000)
            .nrgPrice(10000000000L)
            .value(BigInteger.ONE)
            .nonce(BigInteger.ZERO);

        api.getTx().fastTxbuild(builder.createTxArgs());

        MsgRsp rsp = api.getTx().sendTransaction(null).getObject();
        System.out.println("[1] Created a new multi-sig wallet with address: " + rsp.getTxResult());
        Address wallet = new Address(rsp.getTxResult().toBytes());

        BigInteger walletBalance = api.getChain().getBalance(wallet).getObject();
        BigInteger walletNonce = api.getChain().getNonce(wallet).getObject();
        System.out.println("[1] Current wallet balance: " + walletBalance);
        System.out.println("[1] Current wallet nonce: " + walletNonce);

        // Step 2. Transfer balance to the new wallet.
        System.out.println("[2] Transferring balance to the new wallet...");
        BigInteger senderBalance = api.getChain().getBalance(member1).getObject();
        assertTrue(senderBalance.compareTo(BigInteger.ZERO) >= 0);

        System.out.println("[2] Transferring 10 units to wallet...");
        String data = "1234";
        builder = new TxArgs.TxArgsBuilder()
            .data(ByteArrayWrapper.wrap(data.getBytes()))
            .from(member1)
            .to(wallet)
            .nrgLimit(100000)
            .nrgPrice(10000000000L)
            .value(BigInteger.TEN)
            .nonce(BigInteger.ZERO);

        api.getTx().fastTxbuild(builder.createTxArgs());
        api.getTx().sendTransaction(null).getObject();
        api.getWallet().lockAccount(member1, PW);
        System.out.println("[2] Transfer complete.");

        walletBalance = api.getChain().getBalance(wallet).getObject();
        assertEquals(BigInteger.TEN, walletBalance);
        System.out.println("[2] Current wallet balance: " + walletBalance);

        // Step 3. Attempt to transfer balance from wallet with 1 signature.
        System.out.println("[3] Transferring balance from wallet to recipient with 1 signature...");
        System.out.println("[3] Transferring 2 units...");
        BigInteger amount = BigInteger.TWO;
        walletNonce = api.getChain().getNonce(wallet).getObject();
        byte[] msg = MultiSignatureContract.constructMsg(wallet, walletNonce, to, amount, 10000000000L);
        BigInteger recAmount = api.getChain().getBalance(to).getObject();
        System.out.println("[3] Current recipient balance: " + recAmount);

        List<ISignature> sigs = new ArrayList<>();
        sigs.add(Keystore.getKey(member1.toString(), PW).sign(msg));

        api.getWallet().unlockAccount(member1, PW);
        byte[] send = MultiSignatureContract.constructSendTxInput(wallet, sigs, amount, 10000000000L, to);
        builder = new TxArgs.TxArgsBuilder()
            .data(new ByteArrayWrapper(send))
            .from(member1)
            .to(CONTRACT)
            .nrgLimit(100000)
            .nrgPrice(10000000000L)
            .value(BigInteger.ONE)
            .nonce(BigInteger.ZERO);

        api.getTx().fastTxbuild(builder.createTxArgs());
        rsp = api.getTx().sendTransaction(null).getObject();
        System.out.println("[3] Transfer attempt complete.");
        System.out.println("[3] Transfer error: " + rsp.getError());

        walletBalance = api.getChain().getBalance(wallet).getObject();
        walletNonce = api.getChain().getNonce(wallet).getObject();
        System.out.println("[3] Current wallet balance: " + walletBalance);
        System.out.println("[3] Current wallet nonce: " + walletNonce);

        // Step 4. Attempt to transfer balance from wallet with 2 signatures.
        System.out.println("[4] Transferring balance from wallet to recipient with 2 signatures...");
        System.out.println("[4] Transferring 2 units...");
        amount = BigInteger.TWO;
        walletNonce = api.getChain().getNonce(wallet).getObject();
        msg = MultiSignatureContract.constructMsg(wallet, walletNonce, to, amount, 10000000000L);

        sigs.add(Keystore.getKey(member2.toString(), PW).sign(msg));

        api.getWallet().unlockAccount(member1, PW);
        send = MultiSignatureContract.constructSendTxInput(wallet, sigs, amount, 10000000000L, to);
        builder = new TxArgs.TxArgsBuilder()
            .data(new ByteArrayWrapper(send))
            .from(member1)
            .to(CONTRACT)
            .nrgLimit(100000)
            .nrgPrice(10000000000L)
            .value(BigInteger.ONE)
            .nonce(BigInteger.ZERO);

        api.getTx().fastTxbuild(builder.createTxArgs());
        api.getTx().sendTransaction(null).getObject();
        System.out.println("[4] Transfer complete.");

        walletBalance = api.getChain().getBalance(wallet).getObject();
        walletNonce = api.getChain().getNonce(wallet).getObject();
        recAmount = api.getChain().getBalance(to).getObject();
        System.out.println("[4] Current wallet balance: " + walletBalance);
        System.out.println("[4] Current wallet nonce: " + walletNonce);
        System.out.println("[4] Current recipient balance: " + recAmount);

        api.destroyApi();
    }

    private static void noApi() {
        IRepositoryCache repo = (IRepositoryCache) new DummyRepo();
        ((DummyRepo) repo).storageErrorReturn = null;

        // Step 1. Create a new multi-sig wallet with 3 owners, requiring 2 signatures.
        ECKeyEd25519 acct1 = new ECKeyEd25519();
        ECKeyEd25519 acct2 = new ECKeyEd25519();
        ECKeyEd25519 acct3 = new ECKeyEd25519();

        Address member1 = new Address(acct1.getAddress());
        Address member2 = new Address(acct2.getAddress());
        Address member3 = new Address(acct3.getAddress());
        repo.createAccount(member1);
        repo.addBalance(member1, BigInteger.ONE);
        repo.createAccount(member2);
        repo.addBalance(member1, BigInteger.ONE);
        repo.createAccount(member3);
        repo.addBalance(member1, BigInteger.ONE);
        repo.flush();

        assertNotEquals(member1, member2);
        List<Address> owners = new ArrayList<>();
        owners.add(member1);
        owners.add(member2);
        owners.add(member3);

        System.out.println("Creating the new multi-sig wallet...");
        byte[] input = MultiSignatureContract.constructCreateWalletInput(2, owners);
        MultiSignatureContract msc = new MultiSignatureContract(repo, member1);
        ContractExecutionResult res = msc.execute(input, 21000);
        Address wallet = new Address(res.getOutput());
        System.out.println("Created a new multi-sig wallet with address: " + wallet);

        BigInteger walletBalance = repo.getBalance(wallet);
        BigInteger walletNonce = repo.getNonce(wallet);
        System.out.println("Current wallet balance: " + walletBalance);
        System.out.println("Current wallet nonce: " + walletNonce);

        // Step 2. Transfer balance to the new wallet.
        System.out.println("Transferring 10 units to wallet.");
        repo.addBalance(wallet, BigInteger.TEN);
        repo.flush();
        walletBalance = repo.getBalance(wallet);
        System.out.println("Current wallet balance: " + walletBalance);

        // Step 3. Attempt to transfer balance from wallet with 1 signature.
        BigInteger recAmount = repo.getBalance(member3);
        System.out.println("Transferring balance from wallet to recipient with 1 signature...");
        System.out.println("Transferring 2 units...");
        System.out.println("Current recipient balance: " + recAmount);
        BigInteger amount = BigInteger.TWO;

        byte[] msg = MultiSignatureContract.constructMsg(wallet, repo.getNonce(wallet), member3, amount, 10000000000L);
        List<ISignature> sigs = new ArrayList<>();
        sigs.add(acct1.sign(msg));
        input = MultiSignatureContract.constructSendTxInput(wallet, sigs, amount, 10000000000L, member3);
        res = msc.execute(input, 21000);

        System.out.println("Transfer attempt complete.");
        System.out.println("Transfer error: " + res.getCode());

        walletBalance = repo.getBalance(wallet);
        walletNonce = repo.getNonce(wallet);
        System.out.println("Current wallet balance: " + walletBalance);
        System.out.println("Current wallet nonce: " + walletNonce);

        // Step 4. Attempt to transfer balance from wallet with 2 signatures.
        System.out.println("Transferring balance from wallet to recipient with 2 signatures...");
        System.out.println("Transferring 2 units...");

        sigs.add(acct2.sign(msg));
        input = MultiSignatureContract.constructSendTxInput(wallet, sigs, amount, 10000000000L, member3);
        msc.execute(input, 21000);

        System.out.println("Transfer complete.");

        walletBalance = repo.getBalance(wallet);
        walletNonce = repo.getNonce(wallet);
        recAmount = repo.getBalance(member3);
        System.out.println("Current wallet balance: " + walletBalance);
        System.out.println("Current wallet nonce: " + walletNonce);
        System.out.println("Current recipient balance: " + recAmount);
    }

    public static void main(String[] args) {
        if (false) {
            withApi();
        } else {
            noApi();
        }
    }

}
