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
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ISignature;
import org.aion.mcf.account.Keystore;
import org.aion.precompiled.contracts.MultiSignatureContract;

public class MSCdemo {
    private static final Address CONTRACT = ContractFactory.getMultiSignatureContractAddress();
    private static final String URL = IAionAPI.LOCALHOST_URL;
    private static final String PW = "test";

    private static void t() {
        BigInteger bi = new BigInteger("129");
        byte[] a = bi.toByteArray();
        byte[] b = bi.negate().toByteArray();
        System.out.println(a.length);
        System.out.println(b.length);
        System.out.println(ByteUtil.toHexString(a));
        System.out.println(ByteUtil.toHexString(b));

        byte[] d = grabNegBI(b);
        System.out.println(new BigInteger(d));
    }

    private static byte[] grabNegBI(byte[] a) {
        byte[] res = new byte[128];
        int U = 128 - a.length;

        for (int i = 0; i < 128; i++) {
            if ((i < U) || (a[i - U] == 0)) {
                res[i] = (byte) 0xFF;
            } else {
                res[i] = a[i - U];
            }
        }
        return res;
    }

    public static void main(String[] args) {
        t();
        /*
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
        */
    }

}
