package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.math.BigInteger;
import org.aion.rpc.client.IDGeneratorStrategy;
import org.aion.rpc.client.SimpleIDGenerator;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.AddressBlockParams;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockNumberEnumUnion;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.AddressBlockParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.Uint128Converter;
import org.aion.rpc.types.RPCTypesConverter.Uint128HexStringConverter;
import org.aion.rpc.types.RPCTypesConverter.Uint256HexStringConverter;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class EthRPCImplTest {

    private final AionAddress address =
            new AionAddress(
                    Hex.decode("a0fe13f22d73a80743d9d3afc5b7ed02566a23df12e3f4438f8b46921bc38ef5"));
    private final long blockNumber = 10;
    private final IDGeneratorStrategy idGenerator = new SimpleIDGenerator();
    private final String ethGetAccountBalanceMethod = "eth_getBalance";
    private final String ethGetTransactionCountMethod = "eth_getTransactionCount";
    private ChainHolder chainHolder;
    private RPCServerMethods rpcMethods;

    @Before
    public void setup() {
        chainHolder = mock(ChainHolder.class);
        rpcMethods = new RPCMethods(chainHolder);
        doReturn(BigInteger.TEN).when(chainHolder).getAccountBalance(eq(address), eq(blockNumber));
        doReturn(BigInteger.TWO).when(chainHolder).getAccountNonce(eq(address), eq(blockNumber));

        doReturn(BigInteger.TWO).when(chainHolder).getAccountBalance(eq(address));
        doReturn(BigInteger.ONE).when(chainHolder).getAccountNonce(eq(address));
    }

    @Test
    public void testEth_getAccountBalance() {
        // get account by explicitly setting the block number
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
        assertEquals(
                BigInteger.TWO,
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
        // test the default value
        assertEquals(
                BigInteger.TWO,
                RPCTestUtils.executeRequest(
                        new Request(
                                idGenerator.generateID(),
                                ethGetAccountBalanceMethod,
                                AddressBlockParamsConverter.encode(
                                        new AddressBlockParams(address, null)),
                                VersionType.Version2),
                        rpcMethods,
                        Uint256HexStringConverter::decode));

        // test that mock was called as expected
        Mockito.verify(chainHolder, times(2)).getAccountBalance(any());
        Mockito.verify(chainHolder, times(1)).getAccountBalance(any(), anyLong());
    }

    @Test
    public void testEth_getTransactionCount() {
        // get account by explicitly setting the block number
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
                Uint128HexStringConverter::decode));
        // get account by requesting the best block
        assertEquals(
            BigInteger.ONE,
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
                Uint128HexStringConverter::decode));
        // test the default value
        assertEquals(
            BigInteger.ONE,
            RPCTestUtils.executeRequest(
                new Request(
                    idGenerator.generateID(),
                    ethGetTransactionCountMethod,
                    AddressBlockParamsConverter.encode(
                        new AddressBlockParams(address, null)),
                    VersionType.Version2),
                rpcMethods,
                Uint128HexStringConverter::decode));

        // test that mock was called as expected
        Mockito.verify(chainHolder, times(2)).getAccountNonce(any());
        Mockito.verify(chainHolder, times(1)).getAccountNonce(any(), anyLong());
    }
}
