package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.api.server.external.ChainHolder;
import org.aion.api.server.external.types.SyncInfo;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.Block;
import org.aion.rpc.client.IDGeneratorStrategy;
import org.aion.rpc.client.SimpleIDGenerator;
import org.aion.rpc.constants.Method;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.errors.RPCExceptions.NullReturnRPCException;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes;
import org.aion.rpc.types.RPCTypes.AddressBlockParams;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockNumberEnumUnion;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.CallParams;
import org.aion.rpc.types.RPCTypes.EthBlockHashParams;
import org.aion.rpc.types.RPCTypes.EthBlockNumberParams;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.SendTransactionParams;
import org.aion.rpc.types.RPCTypes.SendTransactionRawParams;
import org.aion.rpc.types.RPCTypes.TransactionHashParams;
import org.aion.rpc.types.RPCTypes.TxCall;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.AddressBlockParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.BigIntHexStringConverter;
import org.aion.rpc.types.RPCTypesConverter.Byte32StringConverter;
import org.aion.rpc.types.RPCTypesConverter.CallParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.DataHexStringConverter;
import org.aion.rpc.types.RPCTypesConverter.EthBlockConverter;
import org.aion.rpc.types.RPCTypesConverter.EthBlockHashParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.EthBlockNumberParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.EthTransactionConverter;
import org.aion.rpc.types.RPCTypesConverter.EthTransactionReceiptConverter;
import org.aion.rpc.types.RPCTypesConverter.LongConverter;
import org.aion.rpc.types.RPCTypesConverter.SendTransactionParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.SendTransactionRawParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.SyncInfoUnionConverter;
import org.aion.rpc.types.RPCTypesConverter.TransactionHashParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.Uint256HexStringConverter;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.HexUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.aion.zero.impl.types.TxResponse;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class EthRPCImplTest {

    private final AionAddress address =
            new AionAddress(
                    Hex.decode("a0fe13f22d73a80743d9d3afc5b7ed02566a23df12e3f4438f8b46921bc38ef5"));
    private final long blockNumber = 10;
    private final long bestBlock = 11;
    private final IDGeneratorStrategy idGenerator = new SimpleIDGenerator();
    private final String ethGetAccountBalanceMethod = "eth_getBalance";
    private final String ethGetTransactionCountMethod = "eth_getTransactionCount";
    private final String ethCallMethod = "eth_call";
    private final String ethBlockNumberMethod = "eth_blockNumber";
    private final String ethSendRawTransactionMethod = "eth_sendRawTransaction";
    private final String ethSendTransactionMethod = "eth_sendTransaction";
    private final String ethSyncingMethod = "eth_syncing";
    private final String ethGetBlockByNumber = Method.Eth_getBlockByNumber.name;
    private final String ethGetBlockByHash = Method.Eth_getBlockByHash.name;
    private final String ethGetTransactionByHash = Method.Eth_getTransactionByHash.name;
    private final String ethGetTransactionReceipt = Method.Eth_getTransactionReceipt.name;
    private ChainHolder chainHolder;
    private RPCServerMethods rpcMethods;
    private AionTxReceipt txReceipt;
    private TxCall txCall0;
    private TxCall txCall1;
    private byte[] txEncoded0;
    private byte[] txEncoded1;
    private AionTransaction transaction1;
    private AionTransaction transaction0;
    private ByteArray transactionHash;
    private StakingBlock emptyPosBlock;

    @Before
    public void setup() {
        chainHolder = mock(ChainHolder.class);
        rpcMethods = new RPCMethods(chainHolder);
        doReturn(bestBlock).when(chainHolder).blockNumber();
        doReturn(BigInteger.TEN).when(chainHolder).getAccountBalance(eq(address), eq(blockNumber));
        doReturn(BigInteger.TWO).when(chainHolder).getAccountNonce(eq(address), eq(blockNumber));

        doReturn(BigInteger.TWO).when(chainHolder).getAccountBalance(eq(address));
        doReturn(BigInteger.ONE).when(chainHolder).getAccountNonce(eq(address));
    }

    private void setupEthCall() {
        doReturn(1_000L).when(chainHolder).getRecommendedNrg();

        txReceipt = new AionTxReceipt();
        txReceipt.setExecutionResult("output".getBytes());

        txCall0 =
                new TxCall(
                        new AionAddress(
                                HexUtil.decode(
                                        "a048bb03f2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                        new AionAddress(
                                HexUtil.decode(
                                        "a048bb03a2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                        ByteArray.wrap(HashUtil.keccak256("data".getBytes())),
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null,
                        null,
                        null,
                    (byte) 1);

        txCall1 =
                new TxCall(
                        new AionAddress(
                                HexUtil.decode(
                                        "a048bb03f2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                        new AionAddress(
                                HexUtil.decode(
                                        "a048bb03a2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                        ByteArray.wrap(HashUtil.keccak256("invalid".getBytes())),
                        BigInteger.ONE,
                        BigInteger.ONE,
                        null,
                        null,
                        null,
                    (byte) 1);

        AionTransaction tx0 = ((RPCMethods) rpcMethods).transactionForTxCall(txCall0);
        AionTransaction tx1 = ((RPCMethods) rpcMethods).transactionForTxCall(txCall1);

        doReturn(txReceipt).when(chainHolder).call(RPCTestUtils.naiveTxMatcher(tx0), any());
        doReturn(null).when(chainHolder).call(RPCTestUtils.naiveTxMatcher(tx1), any());
    }

    @Test
    public void testEth_getAccountBalance() {
        // get account by explicitly setting the block number
        doReturn(BigInteger.TEN).when(chainHolder).getAccountBalance(eq(address), eq(blockNumber));
        assertEquals(
                BigInteger.TEN,
                RPCTestUtils.executeRequest(
                        new Request(
                                idGenerator.generateID(),
                                ethGetAccountBalanceMethod,
                                AddressBlockParamsConverter.encode(
                                        new AddressBlockParams(
                                                address, BlockNumberEnumUnion.wrap(blockNumber))),
                                VersionType.Version2),
                        rpcMethods,
                        Uint256HexStringConverter::decode));
        // get account by requesting the best block
        doReturn(BigInteger.valueOf(12L)).when(chainHolder).getAccountBalance(eq(address), eq(bestBlock));
        assertEquals(
            BigInteger.valueOf(12L),
                RPCTestUtils.executeRequest(
                        new Request(
                                idGenerator.generateID(),
                                ethGetAccountBalanceMethod,
                                AddressBlockParamsConverter.encode(
                                        new AddressBlockParams(
                                                address,
                                                BlockNumberEnumUnion.wrap(BlockEnum.LATEST))),
                                VersionType.Version2),
                        rpcMethods,
                        Uint256HexStringConverter::decode));
        // test the pending value
        doReturn(BigInteger.ONE).when(chainHolder).getAccountBalance(eq(address));
        assertEquals(
                BigInteger.ONE,
                RPCTestUtils.executeRequest(
                        new Request(
                                idGenerator.generateID(),
                                ethGetAccountBalanceMethod,
                                AddressBlockParamsConverter.encode(
                                        new AddressBlockParams(address,
                                            BlockNumberEnumUnion.wrap(BlockEnum.PENDING))),
                                VersionType.Version2),
                        rpcMethods,
                        Uint256HexStringConverter::decode));

        // test the default value
        assertEquals(
            BigInteger.valueOf(12),
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetAccountBalanceMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address, null)),
                    VersionType.Version2),
                rpcMethods,
                Uint256HexStringConverter::decode));

        // test the genesis value
        doReturn(BigInteger.ZERO).when(chainHolder).getAccountBalance(eq(address), eq(0L));
        assertEquals(
            BigInteger.ZERO,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetAccountBalanceMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address,
                            BlockNumberEnumUnion.wrap(BlockEnum.EARLIEST))),
                    VersionType.Version2),
                rpcMethods,
                Uint256HexStringConverter::decode));
        // test that mock was called as expected
        Mockito.verify(chainHolder, times(1)).getAccountBalance(any());
        Mockito.verify(chainHolder, times(4)).getAccountBalance(any(), anyLong());
    }

    @Test
    public void testEth_getTransactionCount() {
        // get account by explicitly setting the block number
        doReturn(BigInteger.TWO).when(chainHolder).getAccountNonce(eq(address),eq(blockNumber));
        assertEquals(
            BigInteger.TWO,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(
                            address, BlockNumberEnumUnion.wrap(blockNumber))),
                    VersionType.Version2),
                rpcMethods,
                BigIntHexStringConverter::decode));
        // get account by requesting the best block
        doReturn(BigInteger.TWO).when(chainHolder).getAccountNonce(eq(address),eq(bestBlock));
        assertEquals(
            BigInteger.TWO,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(
                            address,
                            BlockNumberEnumUnion.wrap(BlockEnum.LATEST))),
                    VersionType.Version2),
                rpcMethods,
                BigIntHexStringConverter::decode));
        // test the default value
        assertEquals(
            BigInteger.TWO,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address, null)),
                    VersionType.Version2),
                rpcMethods,
                BigIntHexStringConverter::decode));


        // test pending value
        doReturn(BigInteger.TEN).when(chainHolder).getAccountNonce(eq(address));
        assertEquals(
            BigInteger.TEN,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address, BlockNumberEnumUnion.wrap(BlockEnum.PENDING))),
                    VersionType.Version2),
                rpcMethods,
                BigIntHexStringConverter::decode));

        // test the genesis value
        doReturn(BigInteger.ZERO).when(chainHolder).getAccountNonce(eq(address), eq(0L));
        assertEquals(
            BigInteger.ZERO,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address,
                            BlockNumberEnumUnion.wrap(BlockEnum.EARLIEST))),
                    VersionType.Version2),
                rpcMethods,
                BigIntHexStringConverter::decode));
        // test that mock was called as expected
        Mockito.verify(chainHolder, times(1)).getAccountNonce(any());
        Mockito.verify(chainHolder, times(4)).getAccountNonce(any(), anyLong());
    }

    @Test
    public void testEth_call() {
        setupEthCall();
        long numForCall = 1_000_000L;
        doReturn(null).when(chainHolder).getBlockByNumber(numForCall);
        // successful call
        Request request0 =
                new Request(
                        idGenerator.generateID(),
                        ethCallMethod,
                        CallParamsConverter.encode(
                                new CallParams(txCall0, BlockNumberEnumUnion.wrap(numForCall))),
                        VersionType.Version2);

        // failed call
        Request request1 =
            new Request(
                idGenerator.generateID(),
                ethCallMethod,
                CallParamsConverter.encode(
                    new CallParams(txCall1, BlockNumberEnumUnion.wrap(numForCall))),
                VersionType.Version2);

        // check that we correctly extracted the output data
        assertEquals(
                ByteArray.wrap(txReceipt.getTransactionOutput()),
                RPCTestUtils.executeRequest(request0, rpcMethods, DataHexStringConverter::decode));

        // check that we get the expected error
        RPCTestUtils.assertFails(
            () -> RPCTestUtils.executeRequest(request1, rpcMethods, DataHexStringConverter::decode),
            NullReturnRPCException.class);
    }

    @Test
    public void testEthBlockNumber(){
        Request request = new Request(
            idGenerator.generateID(),
            ethBlockNumberMethod,
            null,
            VersionType.Version2
        );

        RPCTestUtils.executeRequest(request, rpcMethods, LongConverter::decode);
    }

    private void setupEthSend(){

        txCall0 =
            new TxCall(
                new AionAddress(HexUtil.decode(
                    "a048bb03a2bfdb377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                new AionAddress(
                    HexUtil.decode(
                        "a048bb03a2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                ByteArray.wrap(HashUtil.keccak256("data".getBytes())),
                BigInteger.ZERO,
                BigInteger.ZERO,
                null,
                null,
                null, (byte)1);

        txCall1 =
            new TxCall(
                new AionAddress(
                    HexUtil.decode(
                        "a048bb03f2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                new AionAddress(
                    HexUtil.decode(
                        "a048bb03a2b36b377cbc917422b680a496908fa5efaa0908ae8f432e36dece27")),
                ByteArray.wrap(HashUtil.keccak256("invalid".getBytes())),
                BigInteger.ONE,
                BigInteger.ONE,
                null,
                null,
                null, (byte)1);
        transaction0 = ((RPCMethods)rpcMethods).transactionForTxCall(txCall0);
        transaction1 = ((RPCMethods)rpcMethods).transactionForTxCall(txCall1);

        txEncoded0 = transaction0.getEncoded();
        txEncoded1 = transaction1.getEncoded();
        assertEquals(transaction0, TxUtil.decode(txEncoded0));
        assertEquals(transaction1, TxUtil.decode(txEncoded1));

        //for eth_sendTransaction
        ECKey ecKey = ECKeyFac.inst().create();
        doReturn(ecKey).when(chainHolder).getKey(any());
    }

    @Test
    public void testEthSendRawTransaction(){
        setupEthSend();
        Request request0= new Request(idGenerator.generateID(),
            this.ethSendRawTransactionMethod,
            SendTransactionRawParamsConverter.encode(new SendTransactionRawParams(ByteArray.wrap(txEncoded0))),
            VersionType.Version2);

        Request request1= new Request(idGenerator.generateID(),
            this.ethSendRawTransactionMethod,
            SendTransactionRawParamsConverter.encode(new SendTransactionRawParams(ByteArray.wrap(txEncoded1))),
            VersionType.Version2);

        doReturn(new ImmutablePair<>(transaction0.getTransactionHash(), TxResponse.SUCCESS)).when(chainHolder).sendTransaction(any());
        final ByteArray transactionHash = RPCTestUtils
            .executeRequest(request0, rpcMethods, Byte32StringConverter::decode);

        assertNotNull(transactionHash);
        assertEquals(ByteArray.wrap(transaction0.getTransactionHash()), transactionHash);

        doReturn(new ImmutablePair<>(transaction1.getTransactionHash(), TxResponse.INVALID_ACCOUNT)).when(chainHolder).sendTransaction(any());
        RPCTestUtils.assertFails(() -> RPCTestUtils
            .executeRequest(request1, rpcMethods, Byte32StringConverter::decode), RPCExceptions.TxFailedRPCException.class);
    }

    @Test
    public void testEthSendTransaction(){
        setupEthSend();
        Request request0= new Request(idGenerator.generateID(),
            this.ethSendTransactionMethod,
            SendTransactionParamsConverter.encode(new SendTransactionParams(txCall0)),
            VersionType.Version2);

        Request request1= new Request(idGenerator.generateID(),
            this.ethSendTransactionMethod,
            SendTransactionParamsConverter.encode(new SendTransactionParams(txCall1)),
            VersionType.Version2);

        doReturn(new ImmutablePair<>(transaction0.getTransactionHash(), TxResponse.SUCCESS)).when(chainHolder).sendTransaction(any());
        final ByteArray transactionHash = RPCTestUtils
            .executeRequest(request0, rpcMethods, Byte32StringConverter::decode);

        assertNotNull(transactionHash);
        assertEquals(ByteArray.wrap(transaction0.getTransactionHash()), transactionHash);

        doReturn(new ImmutablePair<>(transaction1.getTransactionHash(), TxResponse.INVALID_ACCOUNT)).when(chainHolder).sendTransaction(any());
        RPCTestUtils.assertFails(() -> RPCTestUtils
            .executeRequest(request1, rpcMethods, Byte32StringConverter::decode), RPCExceptions.TxFailedRPCException.class);
    }

    @Test
    public void testEthSyncInfo(){
        //done syncing
        SyncInfo syncInfo1 = new SyncInfo(true, 0, 10, 10);
        //currently syncing
        SyncInfo syncInfo2 = new SyncInfo(false, 0, 5, 10);

        // kernel is NOT syncing so we expect false
        doReturn(syncInfo1).when(chainHolder).getSyncInfo();
        assertFalse(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethSyncingMethod, null), rpcMethods,
            SyncInfoUnionConverter::decode).done);

        // kernel syncing we expect an object
        doReturn(syncInfo2).when(chainHolder).getSyncInfo();
        final RPCTypes.SyncInfo res = RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethSyncingMethod, null), rpcMethods,
            SyncInfoUnionConverter::decode).syncInfo;
        assertNotNull(res);
        assertEquals(syncInfo2.getChainBestBlkNumber(), res.currentBlock.longValue());
        assertEquals(syncInfo2.getChainStartingBlkNumber(), res.startingBlock.longValue());
        assertEquals(syncInfo2.getNetworkBestBlkNumber(), res.highestBlock.longValue());

        // we expect an error message
        doReturn(null).when(chainHolder).getSyncInfo();
        RPCTestUtils.assertFails(() -> RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethSyncingMethod, null), rpcMethods,
            SyncInfoUnionConverter::decode), NullReturnRPCException.class);
    }

    void setupEthGetBlockOrTransaction(){
        AionTxReceipt receipt = new AionTxReceipt();
        ECKey ecKey = ECKeyFac.inst().create();
        receipt.setError("");
        receipt.setExecutionResult(HashUtil.h256(BigInteger.ONE.toByteArray()));

        List<Log> infos = new ArrayList<>();
        receipt.setLogs(infos);
        receipt.setPostTxState(HashUtil.h256(BigInteger.ONE.toByteArray()));

        AionTxInfo txInfo =
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
        List<AionTransaction> txList = new ArrayList<>();
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
        doReturn(BigInteger.ONE).when(chainHolder).calculateReward(any());
        doReturn(emptyPosBlock).when(chainHolder).getBlockByNumber(anyLong());
        doReturn(emptyPosBlock).when(chainHolder).getBlockByHash(any());
        doReturn(txInfo).when(chainHolder).getTransactionInfo(any());
        doReturn(BigInteger.ONE).when(chainHolder).getTotalDifficultyByHash(any());
        doReturn(new AccountState(BigInteger.TEN, BigInteger.TEN)).when(chainHolder).getAccountState(any());
    }

    @Test
    public void eth_getBlockByNumber(){
        setupEthGetBlockOrTransaction();
        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetBlockByNumber,
            EthBlockNumberParamsConverter.encode(new EthBlockNumberParams(1L, false))),
            rpcMethods,
            EthBlockConverter::decode));

        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetBlockByNumber,
            EthBlockNumberParamsConverter.encode(new EthBlockNumberParams(1L, true))),
            rpcMethods,
            EthBlockConverter::decode));
    }

    @Test
    public void eth_getBlockByHash(){
        setupEthGetBlockOrTransaction();
        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetBlockByHash, EthBlockHashParamsConverter.encode(new EthBlockHashParams(ByteArray.wrap(emptyPosBlock.getHash()), false))), rpcMethods, EthBlockConverter::decode));

        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetBlockByHash,
            EthBlockHashParamsConverter
                .encode(new EthBlockHashParams(ByteArray.wrap(emptyPosBlock.getHash()), true))),
            rpcMethods,
            EthBlockConverter::decode));
    }

    @Test
    public void eth_getTransaction(){
        setupEthGetBlockOrTransaction();
        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetTransactionByHash,
            TransactionHashParamsConverter.encode(new TransactionHashParams((transactionHash)))),
            rpcMethods,
            EthTransactionConverter::decode));
    }

    @Test
    public void eth_getTransactionReceipt(){
        setupEthGetBlockOrTransaction();
        assertNotNull(RPCTestUtils.executeRequest(RPCTestUtils.buildRequest(ethGetTransactionReceipt,
            TransactionHashParamsConverter.encode(new TransactionHashParams((transactionHash)))),
            rpcMethods,
            EthTransactionReceiptConverter::decode));
    }
}
