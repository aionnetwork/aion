package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.aion.api.server.external.ChainHolder;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.Block;
import org.aion.rpc.client.IDGeneratorStrategy;
import org.aion.rpc.client.SimpleIDGenerator;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes;
import org.aion.rpc.types.RPCTypes.AddressParams;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockHashParams;
import org.aion.rpc.types.RPCTypes.BlockNumberParams;
import org.aion.rpc.types.RPCTypes.BlockSpecifierParams;
import org.aion.rpc.types.RPCTypes.BlockSpecifierUnion;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.TransactionHashParams;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.AccountStateConverter;
import org.aion.rpc.types.RPCTypesConverter.AddressParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.BlockDetailsConverter;
import org.aion.rpc.types.RPCTypesConverter.BlockHashParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.BlockNumberParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.BlockSpecifierParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.OpsTransactionConverter;
import org.aion.rpc.types.RPCTypesConverter.TransactionHashParamsConverter;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.junit.Before;
import org.junit.Test;

public class OpsRPCImplTest {
    private ChainHolder holder = mock(ChainHolder.class);
    private RPCMethods opsRPC = new RPCMethods(holder);
    private Block emptyPowBlock;
    private Block emptyPosBlock;
    private AionTxInfo txInfo;
    private List<AionTransaction> txList = new ArrayList<>();
    private ByteArray transactionHash;
    private final IDGeneratorStrategy idGenerator = new SimpleIDGenerator();
    private final String getTransactionMethod = "ops_getTransaction";
    private final String getBlockDetailsByHashMethod = "ops_getBlockDetailsByHash";
    private final String getBlockDetailsByNumberMethod = "ops_getBlockDetailsByNumber";
    private final String getAccountStateMethod = "ops_getAccountState";

    @Before
    public void setup() {
        emptyPowBlock = AionBlock.newEmptyBlock();
        emptyPowBlock.setMainChain();
        AionTxReceipt receipt = new AionTxReceipt();
        ECKey ecKey = ECKeyFac.inst().create();
        receipt.setError("");
        receipt.setExecutionResult(HashUtil.h256(BigInteger.ONE.toByteArray()));

        List<Log> infos = new ArrayList<>();
        receipt.setLogs(infos);
        receipt.setPostTxState(HashUtil.h256(BigInteger.ONE.toByteArray()));

        txInfo =
                AionTxInfo.newInstanceWithInternalTransactions(
                        receipt,
                        ByteArrayWrapper.wrap(HashUtil.h256(BigInteger.ZERO.toByteArray())),
                        0, Collections.emptyList());
        txInfo.getReceipt()
                .setTransaction(
                        AionTransaction.create(ecKey,
                            BigInteger.ZERO.toByteArray(),
                            new AionAddress(ecKey.getAddress()),
                                BigInteger.ZERO.toByteArray(),
                                BigInteger.ZERO.toByteArray(),
                                10,
                                10,
                                (byte) 0b1,
                                HashUtil.h256(BigInteger.ZERO.toByteArray())));
        transactionHash = ByteArray.wrap(txInfo.getReceipt().getTransaction().getTransactionHash());
        txList.add(txInfo.getReceipt().getTransaction());
        StakingBlockHeader.Builder builder =
                StakingBlockHeader.Builder.newInstance()
                        .withDefaultCoinbase()
                        .withDefaultDifficulty()
                        .withDefaultExtraData()
                        .withDefaultLogsBloom()
                        .withDefaultParentHash()
                        .withDefaultReceiptTrieRoot()
                        .withDefaultSeed()
                        .withDefaultSignature()
                        .withDefaultSigningPublicKey()
                        .withDefaultStateRoot()
                        .withDefaultTxTrieRoot();
        emptyPosBlock = new StakingBlock(builder.build(), txList);
        doReturn(BigInteger.ONE).when(holder).calculateReward(any());
        doReturn(emptyPowBlock).when(holder).getBlockByNumber(1);
        doReturn(emptyPosBlock).when(holder).getBlockByNumber(2);
        doReturn(emptyPosBlock).when(holder).getBlockByHash(emptyPosBlock.getHash());
        doReturn(emptyPowBlock).when(holder).getBlockByHash(emptyPowBlock.getHash());
        doReturn(emptyPowBlock).when(holder).getBestBlock();
        doReturn(txInfo).when(holder).getTransactionInfo(any());
        doReturn(emptyPosBlock).when(holder).getBlockByHash(eq(ByteArrayWrapper.wrap(HashUtil.h256(BigInteger.ZERO.toByteArray())).toBytes()));
        doReturn(BigInteger.ONE).when(holder).getTotalDifficultyByHash(any());
        doReturn(new AccountState(BigInteger.TEN, BigInteger.TEN)).when(holder).getAccountState(any());
    }

    @Test
    public void testBlockDetailsEncode() {
        BlockDetailsConverter.encode(opsRPC.ops_getBlockDetails(new BlockSpecifierUnion(1L)));
        BlockDetailsConverter.encode(opsRPC.ops_getBlockDetails(new BlockSpecifierUnion(2L)));
        BlockDetailsConverter.encode(opsRPC.ops_getBlockDetails(
                new BlockSpecifierUnion(ByteArray.wrap(emptyPowBlock.getHash()))));
        System.out.println(
                BlockDetailsConverter.encode(
                        opsRPC.ops_getBlockDetails(
                                new BlockSpecifierUnion(ByteArray.wrap(emptyPosBlock.getHash())))));
        BlockDetailsConverter.encode(opsRPC.ops_getBlockDetails(
            new BlockSpecifierUnion(BlockEnum.LATEST)));
    }

    @Test
    public void testOps_getBlockDetails() {
        assertNotNull(execute(
            new Request(
                idGenerator.generateID(),
                "ops_getBlockDetails",
                BlockSpecifierParamsConverter.encode(new BlockSpecifierParams(new BlockSpecifierUnion(1L))),
                VersionType.Version2),
                BlockDetailsConverter::decode));

        assertNotNull(execute(
            new Request(
                idGenerator.generateID(),
                "ops_getBlockDetails",
                BlockSpecifierParamsConverter.encode(BlockSpecifierParamsConverter.decode("[latest]")),
                VersionType.Version2),
            BlockDetailsConverter::decode));

        assertNotNull(execute(
            new Request(
                idGenerator.generateID(),
                "ops_getBlockDetails",
                BlockSpecifierParamsConverter.encode(
                    BlockSpecifierParamsConverter.decode(
                        "{\"block\": \""
                            + ByteArray.wrap(emptyPowBlock.getHash())
                            + "\"}")),
                VersionType.Version2),
            BlockDetailsConverter::decode));
    }

    @Test
    public void testOps_getBlockDetailsByHash() {
        assertNotNull(execute(
            new Request(
                idGenerator.generateID(),
                getBlockDetailsByHashMethod,
                BlockHashParamsConverter.encode(new BlockHashParams(ByteArray.wrap(emptyPowBlock.getHash()))),
                VersionType.Version2),
            BlockDetailsConverter::decode));
    }

    @Test
    public void testOps_getBlockDetailsByNumber() {
        assertNotNull(execute(
            new Request(
                idGenerator.generateID(),
                getBlockDetailsByNumberMethod,
                BlockNumberParamsConverter.encode(new BlockNumberParams(1L)),
                VersionType.Version2),
            BlockDetailsConverter::decode));
    }

    @Test
    public void testOps_getTransaction(){
        Request request = new Request(idGenerator.generateID(), getTransactionMethod,
            TransactionHashParamsConverter.encode(new TransactionHashParams(transactionHash)), VersionType.Version2);
        assertNotNull(execute(request, OpsTransactionConverter::decode));
    }

    @Test
    public void testOps_getAccountState(){
        AionAddress aionAddress = new AionAddress(ByteUtil.hexStringToBytes("a07913c03686c9659c1b614d098fd1db380a52b71fd58526b53d8107f7b355d5"));
        Request request = new Request(idGenerator.generateID(), getAccountStateMethod,
            AddressParamsConverter.encode(new AddressParams(aionAddress)), VersionType.Version2);
        final RPCTypes.AccountState result = execute(request, AccountStateConverter::decode);
        assertNotNull(result);
        assertEquals(aionAddress, result.address);
        assertEquals(BigInteger.TEN, result.balance);
        assertEquals(BigInteger.TEN, result.nonce);
    }

    private <T> T execute(Request request, Function<Object, T> extractor){
        return  extractor.apply(RPCServerMethods.execute(request, opsRPC));
    }
}
