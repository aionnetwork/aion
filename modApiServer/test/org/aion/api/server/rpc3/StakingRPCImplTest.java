package org.aion.api.server.rpc3;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.Arrays;
import org.aion.api.server.external.AionChainHolder;
import org.aion.api.server.external.ChainHolder;
import org.aion.api.server.external.account.AccountManagerInterface;
import org.aion.rpc.errors.RPCExceptions.BlockTemplateNotFoundRPCException;
import org.aion.rpc.errors.RPCExceptions.NullReturnRPCException;
import org.aion.rpc.errors.RPCExceptions.UnsupportedUnityFeatureRPCException;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.SubmitSeedParams;
import org.aion.rpc.types.RPCTypes.SubmitSignatureParams;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypes.VoidParams;
import org.aion.rpc.types.RPCTypesConverter;
import org.aion.rpc.types.RPCTypesConverter.BoolConverter;
import org.aion.rpc.types.RPCTypesConverter.SubmitSeedParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.SubmitSignatureParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.VoidParamsConverter;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class StakingRPCImplTest {
    private ChainHolder chainHolder;
    private RPCMethods rpcMethods;
    private ByteArray validSignature;
    private ByteArray validSealHash;
    private ByteArray invalidSignature;
    private ByteArray invalidSealHash;
    private ByteArray invalidSeed;
    private ByteArray invalidSigningPublicKey;
    private AionAddress invalidCoinbase;
    private ByteArray validSeed;
    private ByteArray validSigningPublicKey;
    private AionAddress validCoinbase;
    private byte[] valid64Bytes;
    private ByteArray missingSealHash;
    private AccountManagerInterface accountManager = Mockito.mock(AccountManagerInterface.class);

    @Before
    public void setup(){

        chainHolder = mock(ChainHolder.class);
        rpcMethods = new RPCMethods(chainHolder);
        // creating valid input for submit signature
        valid64Bytes = new byte[64];
        Arrays.fill(valid64Bytes, (byte)0b0000_1000);
        validSignature = ByteArray.wrap(valid64Bytes);
        byte[] valid32Bytes = new byte[32];
        Arrays.fill(valid32Bytes, (byte)0b0000_1000);
        validSealHash = ByteArray.wrap(valid32Bytes);
        doReturn(true).when(chainHolder).submitSignature(valid64Bytes, valid32Bytes);
        // creating invalid input for submit signature
        byte[] invalid64Bytes = new byte[64];
        Arrays.fill(invalid64Bytes, (byte)0b0000_0000);
        invalidSignature = ByteArray.wrap(invalid64Bytes);
        byte[] invalid32Bytes = new byte[32];
        Arrays.fill(invalid32Bytes, (byte)0b0000_0000);
        invalidSealHash = ByteArray.wrap(invalid32Bytes);
        byte[] missing32Bytes = new byte[32];
        Arrays.fill(missing32Bytes, (byte)0b1100_0000);
        missingSealHash = ByteArray.wrap(missing32Bytes);

        doReturn(false).when(chainHolder).submitSignature(invalid64Bytes, invalid32Bytes);
        //creating input for submit seed
        invalidSeed = ByteArray.wrap(invalid64Bytes);
        invalidCoinbase = new AionAddress(Hex.decode("a0"+Hex.toHexString(invalid32Bytes).substring(2)));
        invalidSigningPublicKey = ByteArray.wrap(invalid32Bytes);
        doReturn(null).when(chainHolder).submitSeed(invalidSeed.toBytes(), invalidSigningPublicKey.toBytes(),invalidCoinbase.toByteArray());

        validSeed = ByteArray.wrap(valid64Bytes);
        validCoinbase = new AionAddress(Hex.decode("a0"+Hex.toHexString(valid32Bytes).substring(2)));
        validSigningPublicKey = ByteArray.wrap(valid32Bytes);
        doReturn(valid64Bytes).when(chainHolder).submitSeed(validSeed.toBytes(), validSigningPublicKey.toBytes(),validCoinbase.toByteArray());
        //stubbing getSeed
        doReturn(valid64Bytes).when(chainHolder).getSeed();
        doReturn(true).when(chainHolder).isUnityForkEnabled();
        doReturn(true).when(chainHolder).canSeal(validSealHash.toBytes());
        doReturn(true).when(chainHolder).canSeal(invalidSealHash.toBytes());
        doReturn(false).when(chainHolder).canSeal(missingSealHash.toBytes());
    }

    @Test
    public void testSubmitSignature(){
        assertTrue(RPCTestUtils.executeRequest(new Request(1, "submitsignature", SubmitSignatureParamsConverter.encode(new SubmitSignatureParams(validSignature, validSealHash)), VersionType.Version2),
            rpcMethods,
            BoolConverter::decode));
        assertFalse(RPCTestUtils.executeRequest(new Request(1, "submitsignature", SubmitSignatureParamsConverter.encode(new SubmitSignatureParams(invalidSignature, invalidSealHash)), VersionType.Version2),
            rpcMethods,
            BoolConverter::decode));
        RPCTestUtils.assertFails(
            ()->  RPCTestUtils.executeRequest(new Request(1, "submitsignature", SubmitSignatureParamsConverter.encode(new SubmitSignatureParams(invalidSignature,missingSealHash)), null),
                rpcMethods,
                BoolConverter::decode),
            BlockTemplateNotFoundRPCException.class
        );
    }

    @Test
    public void testSubmitSeed(){
        assertEquals(ByteArray.wrap(valid64Bytes), RPCTestUtils.executeRequest(new Request(1, "submitseed", SubmitSeedParamsConverter
            .encode(new SubmitSeedParams(validSeed, validSigningPublicKey, validCoinbase)), VersionType.Version2), rpcMethods,RPCTypesConverter.ByteArrayConverter::decode));
        RPCTestUtils.assertFails(()-> RPCTestUtils.executeRequest(new Request(1, "submitseed", SubmitSeedParamsConverter
            .encode(new SubmitSeedParams(invalidSeed, invalidSigningPublicKey, invalidCoinbase)), VersionType.Version2), rpcMethods, RPCTypesConverter.ByteArrayConverter::decode),
            NullReturnRPCException.class);
    }

    @Test
    public void testGetSeed(){
        assertNotNull(RPCTestUtils.executeRequest(new Request(1, "getseed", VoidParamsConverter.encode(
            new VoidParams()), VersionType.Version2), rpcMethods, RPCTypesConverter.ByteArrayConverter::decode));
    }

    @Test
    public void  testCallUnityFeatureBeforeFork(){
        chainHolder = spy(new AionChainHolder(AionImpl.instForTest(), accountManager));
        doReturn(false).when(chainHolder).isUnityForkEnabled();
        doCallRealMethod().when(chainHolder).getSeed();
        rpcMethods= new RPCMethods(chainHolder);
        //This call will throw because a unity feature is requested before
        //The unity fork
        RPCTestUtils.assertFails(()-> {
                Request request = new Request(1, "getseed", null, VersionType.Version2);
                return RPCTestUtils.executeRequest(request,
                    rpcMethods,
                    RPCTypesConverter.ByteArrayConverter::decode);
            },
            UnsupportedUnityFeatureRPCException.class);
    }
}
