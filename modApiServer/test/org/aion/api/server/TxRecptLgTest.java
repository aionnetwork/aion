package org.aion.api.server;

import static junit.framework.TestCase.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import org.aion.api.server.types.TxRecptLg;
import org.aion.base.AionTransaction;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

public class TxRecptLgTest {

    private static byte[] readContract(String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InputStream in = TxRecptLgTest.class.getResourceAsStream(fileName);
        for (int c; (c = in.read()) != -1; ) {
            out.write(c);
        }
        in.close();

        return out.toByteArray();
    }

    @Test
    public void TestTxRecptLg() throws InterruptedException, IOException {

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // ======================
        // DEPLOY contract A & B
        // ======================
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                readContract("contract/contract.sol"),
                                Compiler.Options.ABI,
                                Compiler.Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String contractA = cr.contracts.get("A").bin;
        String contractB = cr.contracts.get("B").bin;

        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx1 =
                new AionTransaction(
                        nonce.toByteArray(),
                        new AionAddress(deployerAccount.getAddress()),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        1L);
        tx1.sign(deployerAccount);

        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx2 =
                new AionTransaction(
                        nonce.toByteArray(),
                        new AionAddress(deployerAccount.getAddress()),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractB),
                        1_000_000L,
                        1L);
        tx2.sign(deployerAccount);

        BlockContext context =
                bc.createNewBlockContext(bc.getBestBlock(), List.of(tx1, tx2), false);
        ImportResult result = bc.tryToConnect(context.block);
        assertEquals(result, ImportResult.IMPORTED_BEST);

        AionAddress addressA = TxUtil.calculateContractAddress(tx1);
        System.out.println("contract A address = " + addressA);
        AionAddress addressB = TxUtil.calculateContractAddress(tx2);
        System.out.println("contract B address = " + addressB);
        Thread.sleep(1000);

        // ======================
        // CALL function A.AA
        // ======================
        nonce = nonce.add(BigInteger.ONE);
        byte[] functionAA = new byte[4];
        System.arraycopy(HashUtil.keccak256("AA(address)".getBytes()), 0, functionAA, 0, 4);
        AionTransaction tx3 =
                new AionTransaction(
                        nonce.toByteArray(),
                        new AionAddress(deployerAccount.getAddress()),
                        addressA,
                        new byte[0],
                        ByteUtil.merge(functionAA, addressB.toByteArray()),
                        1_000_000L,
                        1L);
        tx3.sign(deployerAccount);

        context = bc.createNewBlockContext(bc.getBestBlock(), List.of(tx3), false);
        result = bc.tryToConnect(context.block);
        assertEquals(result, ImportResult.IMPORTED_BEST);

        AionTxInfo info = bc.getTransactionInfo(tx3.getTransactionHash());
        AionTxReceipt receipt = info.getReceipt();
        System.out.println(receipt);
        assertEquals(4, receipt.getLogInfoList().size());

        // ======================
        //  Test
        // ======================
        TxRecptLg[] logs = new TxRecptLg[receipt.getLogInfoList().size()];
        for (int i = 0; i < logs.length; i++) {
            Log logInfo = receipt.getLogInfoList().get(i);
            logs[i] =
                    new TxRecptLg(
                            logInfo,
                            context.block,
                            info.getIndex(),
                            receipt.getTransaction(),
                            i,
                            true);
        }
        String ctAddrA = "0x" + addressA.toString();
        String ctAddrB = "0x" + addressB.toString();

        assertEquals(ctAddrA, logs[0].address); // AE
        assertEquals(ctAddrA, logs[1].address); // AEA
        assertEquals(ctAddrB, logs[2].address); // b.BB
        assertEquals(ctAddrA, logs[3].address); // AEB
    }
}
