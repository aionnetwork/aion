package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.function.Function;
import org.aion.api.server.external.account.AccountManagerInterface;
import org.aion.api.server.external.AionChainHolder;
import org.aion.api.server.external.ChainHolder;
import org.aion.crypto.HashUtil;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.AddressParams;
import org.aion.rpc.types.RPCTypes.BlockTemplate;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.MinerStats;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.SubmissionResult;
import org.aion.rpc.types.RPCTypes.SubmitBlockParams;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypes.VoidParams;
import org.aion.rpc.types.RPCTypesConverter;
import org.aion.rpc.types.RPCTypesConverter.AddressConverter;
import org.aion.rpc.types.RPCTypesConverter.AddressParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.BigIntConverter;
import org.aion.rpc.types.RPCTypesConverter.BlockTemplateConverter;
import org.aion.rpc.types.RPCTypesConverter.MinerStatsConverter;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;
import org.aion.rpc.types.RPCTypesConverter.SubmissionResultConverter;
import org.aion.rpc.types.RPCTypesConverter.SubmitBlockParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.VoidParamsConverter;
import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class MiningRPCImplTest {

    private final ByteArray blockThatCanBeSealed =
            ByteArray.wrap(HashUtil.keccak256(BigInteger.ZERO.toByteArray()));
    private final ByteArray blockThatDoesNotExistInCache =
            ByteArray.wrap(HashUtil.keccak256(BigInteger.ONE.toByteArray()));
    private final ByteArray blockThatCannotBeSealed =
            ByteArray.wrap(HashUtil.keccak256(BigInteger.TWO.toByteArray()));
    private final ByteArray blockThatWillThrow =
            ByteArray.wrap(HashUtil.keccak256(BigInteger.valueOf(3).toByteArray()));
    private final ByteArray equihashSolution =
            ByteArray.wrap(
                    "0039f0a9cc00dbd3845c0e7cefa6412b67d45b27551b3ec313f1a3a6e4333205ac98a83aed9d9c179bfe90a400eaac8a93960271dc0be45e3bfec5447a5ae1331b303b146afc9d04a3e49c0e9a5f4d3f9bab68e88d26c951122329ae1df166f1b0840f3d7886a11d5ac223ed23621ae4458928ab460b3297fd4e3203a165f548cb5753881a0628dda84647467b918b216d3560553cc8eb3e3da6271e1bbc431558bdb338de84bb0bb362dacb12772ed1045b072403111e827fecec275c850159e34e610de5d7392ad9977a03ffb466bb123fb127cd683b0e8435921019562977d5023f32ab87c02a2a63e1b878e367f51bc93e06a245b0952da6f573fc5a88fe3ed8c93bfd298c6c13f67c7be49432235fdb294c9287c23c4c5446f2daf92814ebe2df5613f6d8c5363a91513183b88e21ffeec5191fcd77b40bf0fdf5abf61bffc61115680f8df83a01334842e900d52aa039038a3e4535b4befa24c27b6fdc00b5a3080ff13f8305c03814a7f6247a24e3a82f8aa2253860d28522cbd332d646461a839b6e164d8c61447d25702fcead18668f73f02f42f60241e37e589bff1bc253fe5dee0b79110beee232847ee63599f8a5f739c24f0330d77c770af9a5bb35f92c4bcacbc2299109fe8788211be4ad09d5bb847b695c2e3279f8af172b8aec95fb325cced97e59d79bf969dc8eac078d9499c19c2fc2ea58015a4f3659427cbdbb527a2f6e7c447a2fa171d5e702d6549bd571e23894031318c0ffcbc305f718e6362b05469d915eb59773b6370e0f94a9c898a4fd3676d8851bb204b71a32569fdea0801d7d75941d846d1156a6a7467d0ee031654f75f279de9be336b37cbaffb7f38bdd02ed5885ed4dd3e8b8fc8c5d4465f214b6390b738c6e25650343b8697f2cb9b1454683439bb295a4be3a5f530606418dfa9504c6620fff7287226aa9bd2381b7635e4191234633941ad963e38545d7e521f69717d0b226e604d2eb2250e08df07be73505c9f10fe18077b5ecc7bd5b242266e33871f8752c8b9df08ba7abcda8bbfebdd3083e00c09aec97cf3e562e172fec823cb4e72c67bc2708a2859aafbe8ade7eed3f7056ef31190ab16bb1f1a7089dc83d10e6af967a05fc9322e2568f5bade23733c83dca436fb739b4d5aa405141f2e26f457a28d7bf97d310d526f2c4f570cb7dc1465a85759be65b9254fbba183036d0e5e653559b305a45acc76bb6d3fb69d03a86100637be831e3733d96ce6a10a252acdc6c2a6e5356dbb18012af929b45105f35bb1537b0dbb45ebab483eeeed22fb120576ab1f1a7478ed3afa1161e477f1ce7d9fed38945f42ced7d6be6b39b48f60ef78c7fcd148bf0c4f12ec9e8214b568c0b38aa135ef6520823518e0da28f22e4530dbf4d6fdaaf90e3a87719eda762d7a83be4f8320be7bab12528cf7f9788321347ab36749d0d12cb00f22526897275a34b4fbb6162a543d6101055311ec502304fa6b190990b15e15c0a22cda9f8f89f4159a70123f236e2d995d0c486539a67467bd72ddff47930bbe3596056191d4f1b8a3dcb777c408ed0a6661f8d29a6425e806c8ddc5ddc61872d8eb1529f25b30ba24f58824f34d05ff10ae09982056a4fb60095926fde5c11143df3a3652bddba0720b7facb330d6831e6fbe1c2d7331bb321106e8abbebda3a14222745e0ad631afd5f0c755470c7e45479bc8650684b35aed255fadc2dfcc7baccbbab020055c5689ff88a90e708280266e9db98286f40c6e6fe5064cd27e7411c27e3da8910fdbe2c0b925d702d968860f8e9a488d041d326de1c14dfd970290ea34833d075c1119af0995b90dbd71cb5d15bd5ca509652fd4e2f2e90b132eaad773a2ae1874570b787e3139b5372e5a9718115c77dd04514c16bad37b54a82a5abc9733dd5f2ce00e9eabdac8c19b318cc63559d5ae1419385674348375331e43c08c9af38ab281b24e8b8221f4c563afd98a19");
    private final ByteArray nonce =
            ByteArray.wrap("c5110000000000000000000000000000000000000000000072acb5c410b6ca2e");
    private RPCMethods rpcMethods;
    private AccountManagerInterface accountManager = Mockito.mock(AccountManagerInterface.class);

    @Before
    public void setup() {
        ChainHolder chainHolder = mock(ChainHolder.class);
        MinerStatisticsCalculator minerStatisticsCalculator = mock(MinerStatisticsCalculator.class);
        doReturn(
                        new MinerStats(
                                BigInteger.ONE.toString(),
                                BigInteger.ONE.toString(),
                                BigInteger.ONE.toString()))
                .when(minerStatisticsCalculator)
                .getStats(any());
        rpcMethods = new RPCMethods(chainHolder, minerStatisticsCalculator);
        //Set up mocks
        doReturn(true).when(chainHolder).canSeal(blockThatCanBeSealed.toBytes());
        doReturn(true).when(chainHolder).canSeal(blockThatCannotBeSealed.toBytes());
        doReturn(false).when(chainHolder).canSeal(blockThatDoesNotExistInCache.toBytes());
        doReturn(true).when(chainHolder).canSeal(blockThatWillThrow.toBytes());

        doReturn(true)
                .when(chainHolder)
                .submitBlock(
                        nonce.toBytes(),
                        equihashSolution.toBytes(),
                        blockThatCanBeSealed.toBytes());
        doReturn(false)
                .when(chainHolder)
                .submitBlock(
                        nonce.toBytes(),
                        equihashSolution.toBytes(),
                        blockThatCannotBeSealed.toBytes());
        doThrow(RuntimeException.class)
                .when(chainHolder)
                .submitBlock(
                        nonce.toBytes(), equihashSolution.toBytes(), blockThatWillThrow.toBytes());
    }

    @Test
    public void testGetMinerStatsMock() {
        MinerStats minerStats =
            RPCTestUtils.executeRequest(buildRequest("getMinerStatistics",
                            AddressParamsConverter.encode(new AddressParams(AddressConverter.decode("0xa0c5bf6c4779bf8c2e0a3ff71353d09b066db2b5876ee2345efb836510b3126b")))),
                rpcMethods,
                MinerStatsConverter::decode);
        assertNotNull(minerStats);
        assertEquals("1", minerStats.minerHashrate);
        assertEquals("1", minerStats.minerHashrateShare);
        assertEquals("1", minerStats.networkHashRate);
    }

    @Test
    public void testSubmitSolution() {
        String method = "submitBlock";
        try {
            Request request =
                    buildRequest(method,
                            SubmitBlockParamsConverter.encode(new SubmitBlockParams(nonce, equihashSolution, blockThatDoesNotExistInCache)));
            RPCTestUtils.executeRequest(request, rpcMethods,SubmissionResultConverter::decode);
            fail();
        } catch (RPCExceptions.BlockTemplateNotFoundRPCException e) {
            /*We expect this error*/
        }

        Request request1 =
                buildRequest(method,
                        SubmitBlockParamsConverter.encode(new SubmitBlockParams(nonce, equihashSolution, blockThatCanBeSealed)));

        SubmissionResult submissionResult = RPCTestUtils.executeRequest(request1, rpcMethods,SubmissionResultConverter::decode);
        assertNotNull(submissionResult);
        assertTrue(submissionResult.result);

        request1 =
                buildRequest(method,
                    SubmitBlockParamsConverter.encode(new SubmitBlockParams(nonce, equihashSolution, blockThatCannotBeSealed)));
        submissionResult = RPCTestUtils.executeRequest(request1, rpcMethods,SubmissionResultConverter::decode);
        assertNotNull(submissionResult);
        assertFalse(submissionResult.result); // check that the result can be encoded for return

        try {
            request1 =
                    buildRequest(method,
                            SubmitBlockParamsConverter.encode(
                                    new SubmitBlockParams(nonce, equihashSolution, blockThatWillThrow)));
            RPCTestUtils.executeRequest(request1, rpcMethods,SubmissionResultConverter::decode);
            fail();
        } catch (RPCExceptions.FailedToSealBlockRPCException e) {
            /*We expect this error*/
        }
    }

    private Request buildRequest(String method, Object object) {
        // Check that the request can be decoded and encoded
        // This is tests the underlying rpc library
        return RequestConverter.decode(
                RequestConverter.encode(new Request(1, method, object, VersionType.Version2)));
    }

    @Test
    public void getWorkTest(){
        ChainHolder chainHolder = new AionChainHolder(AionImpl.instForTest(), accountManager);
        rpcMethods=new RPCMethods(chainHolder);
        final BlockTemplate blockTemplate = rpcMethods.getBlockTemplate();
        assertNotNull(blockTemplate);
        assertTrue(chainHolder.canSeal(blockTemplate.headerHash.toBytes()));
        BlockTemplateConverter.encode(blockTemplate);

        final Request request = buildRequest("getBlockTemplate", VoidParamsConverter.encode(new VoidParams()));
        BlockTemplateConverter.encode(RPCTestUtils.executeRequest(request, rpcMethods,BlockTemplateConverter::decode));
    }

    @Test
    public void getDifficulty(){
        ChainHolder chainHolder = new AionChainHolder(AionImpl.instForTest(), accountManager);
        rpcMethods=new RPCMethods(chainHolder);
        final BigInteger difficulty = rpcMethods.getDifficulty();
        assertNotNull(difficulty);
        final Request request = buildRequest("getDifficulty", VoidParamsConverter.encode(new VoidParams()));
        BigIntConverter.encode(RPCTestUtils.executeRequest(request, rpcMethods,RPCTypesConverter.BigIntConverter::decode));
    }
}
