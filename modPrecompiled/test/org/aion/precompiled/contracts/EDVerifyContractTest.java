package org.aion.precompiled.contracts;

/*
 * @author Centrys
 */
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.fastvm.ExecutionContext;
import org.aion.mcf.config.CfgFork;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.type.PrecompiledContract;

import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class EDVerifyContractTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 2000001;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWordImpl blockDifficulty = new DataWordImpl(0x100000000L);

    private DataWordImpl nrgPrice;
    private long nrgLimit;
    private DataWordImpl callValue;
    private byte[] callData;
    private byte[] pubKey;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private File forkFile;

    @Before
    public void setup() throws IOException {
        nrgPrice = DataWordImpl.ONE;
        nrgLimit = 20000;
        callValue = DataWordImpl.ZERO;
        callData = new byte[0];

        new File(System.getProperty("user.dir") + "/mainnet/config").mkdirs();
        forkFile =
                new File(
                        System.getProperty("user.dir")
                                + "/mainnet/config"
                                + CfgFork.FORK_PROPERTIES_PATH);
        forkFile.createNewFile();

        // Open given file in append mode.
        BufferedWriter out = new BufferedWriter(new FileWriter(forkFile, true));
        out.write("fork0.3.2=2000000");
        out.close();

        CfgAion.inst();
    }

    @After
    public void teardown() {
        forkFile.delete();
        forkFile.getParentFile().delete();
        forkFile.getParentFile().getParentFile().delete();
    }

    @Test
    public void shouldReturnSuccessTestingWith256() {
        byte[] input = setupInput();
        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        txHash,
                        ContractFactory.getEdVerifyContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);
        PrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        TransactionResult result = contract.execute(input, 21000L);
        assertThat(result.getResultCode().isSuccess());
        assertThat(Arrays.equals(result.getReturnData(), pubKey));
    }

    @Test
    public void emptyInputTest() {
        byte[] input = new byte[128];

        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        txHash,
                        ContractFactory.getEdVerifyContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);
        PrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        TransactionResult result = contract.execute(input, 21000L);
        assertThat(result.getResultCode().isSuccess());
        assertThat(Arrays.equals(result.getReturnData(), pubKey));
    }

    @Test
    public void incorrectInputTest() {
        byte[] input = setupInput();

        input[22] = (byte) ((int) (input[32]) - 10); // modify sig
        input[33] = (byte) ((int) (input[33]) + 4); // modify sig
        input[99] = (byte) ((int) (input[33]) - 40); // modify sig

        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        txHash,
                        ContractFactory.getEdVerifyContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);
        PrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        TransactionResult result = contract.execute(input, 21000L);
        assertThat(result.getResultCode().isSuccess());
        assertThat(Arrays.equals(result.getReturnData(), Address.ZERO_ADDRESS().toBytes()));
    }

    @Test
    public void shouldFailIfNotEnoughEnergy() {
        nrgPrice = DataWordImpl.ONE;

        byte[] input = setupInput();
        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        txHash,
                        ContractFactory.getEdVerifyContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);
        PrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        TransactionResult result = contract.execute(input, 2999L);
        assertThat(result.getResultCode().toInt())
                .isEqualTo(PrecompiledResultCode.OUT_OF_NRG.toInt());
    }

    @Test
    public void invalidInputLengthTest() {
        byte[] input = new byte[129]; // note the length is 129
        input[128] = 0x1;

        ExecutionContext ctx =
                new ExecutionContext(
                        null,
                        txHash,
                        ContractFactory.getEdVerifyContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);
        PrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        TransactionResult result = contract.execute(input, 21000L);

        assertThat(result.getResultCode().toInt()).isEqualTo(PrecompiledResultCode.FAILURE.toInt());
    }

    private byte[] setupInput() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey =
                ecKey.fromPrivate(
                        Hex.decode(
                                "5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        pubKey = ecKey.getPubKey();

        byte[] data = "Our first test in AION1234567890".getBytes();

        HashUtil.setType(HashUtil.H256Type.KECCAK_256);
        byte[] hashedMessage = HashUtil.h256(data);

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[128];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(pubKey, 0, input, 32, 32);
        System.arraycopy(signature.getSignature(), 0, input, 64, 64);

        return input;
    }
}
