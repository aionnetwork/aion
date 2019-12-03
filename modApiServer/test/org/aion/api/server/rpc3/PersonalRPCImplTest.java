package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aion.api.server.external.AionChainHolder;
import org.aion.api.server.external.ChainHolder;
import org.aion.api.server.external.account.AccountManager;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rpc.client.SimpleIDGenerator;
import org.aion.rpc.errors.RPCExceptions.InvalidParamsRPCException;
import org.aion.rpc.errors.RPCExceptions.MethodNotFoundRPCException;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.EcRecoverParams;
import org.aion.rpc.types.RPCTypes.LockAccountParams;
import org.aion.rpc.types.RPCTypes.ParamUnion;
import org.aion.rpc.types.RPCTypes.PasswordParams;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.UnlockAccountParams;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypes.VoidParams;
import org.aion.rpc.types.RPCTypesConverter.AddressConverter;
import org.aion.rpc.types.RPCTypesConverter.AddressListConverter;
import org.aion.rpc.types.RPCTypesConverter.BoolConverter;
import org.aion.rpc.types.RPCTypesConverter.DataHexStringConverter;
import org.aion.rpc.types.RPCTypesConverter.EcRecoverParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.LockAccountParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.PasswordParamsConverter;
import org.aion.rpc.types.RPCTypesConverter.UnlockAccountParamsConverter;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.Before;
import org.junit.Test;

public class PersonalRPCImplTest {

    private ChainHolder chainHolder;
    private RPCMethods rpc;
    private SimpleIDGenerator idGenerator = new SimpleIDGenerator();
    private final String unlockAccountMethod = "personal_unlockAccount";
    private final String newAccountMethod = "personal_newAccount";
    private final String lockAccountMethod = "personal_lockAccount";
    private final String listAccountMethod = "personal_listAccounts";
    private final String ecRecoverMethod = "personal_ecRecover";

    @Before
    public void setup() {
        chainHolder = mock(ChainHolder.class);
        rpc = spy(new RPCMethods(chainHolder));
    }

    @Test
    public void personal_ecRecover() {
        ECKey ecKey = ECKeyFac.inst().create();
        String helloMessage = "Hello World";
        String byeMessage = "Bye World";
        ByteArray helloByteMessage = ByteArray.wrap(helloMessage.getBytes());
        ByteArray byeByteMessage = ByteArray.wrap(byeMessage.getBytes());

        //Create the signed message
        ByteArray signedMessage =
            ByteArray.wrap(ecKey.sign(helloByteMessage.toBytes()).toBytes());

        //append 0x to make params valid
        String hexString = "0x" + ByteUtil.toHexString(signedMessage.toBytes());
        String pubKey = "0x" + ByteUtil.toHexString(ecKey.getAddress());

        //recover public key and validate that the message was signed
        AionAddress recoveredKey = rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertEquals(AddressConverter.decode(pubKey), recoveredKey);

        //attempt to recover the public key and fail since the incorrect message was used
        recoveredKey = rpc.personal_ecRecover(byeByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertNotEquals(AddressConverter.decode(pubKey), recoveredKey);

        try{
            //attempt to recover a pk with an incorrect signature
            rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString+"1"));
            fail();
        }catch (Exception e){
            //We expect an exception
        }
    }

    @Test
    public void executePersonal_ecRecover() {
        ECKey ecKey = ECKeyFac.inst().create();
        String helloMessage = "Hello World";
        ByteArray helloByteMessage = ByteArray.wrap(helloMessage.getBytes());

        // Create the signed message
        ByteArray signedMessage =
            ByteArray.wrap(ecKey.sign(helloByteMessage.toBytes()).toBytes());

        String pubKey = ByteUtil.toHexString(ecKey.getAddress());
        // well formed request
        Request request =
            new Request(
                idGenerator.generateID(),
                ecRecoverMethod,
                EcRecoverParamsConverter.encode(new EcRecoverParams(helloByteMessage, signedMessage)),
                VersionType.Version2);
        assertEquals(pubKey, RPCTestUtils.executeRequest(request, rpc,AddressConverter::decode).toString());

        // incorrect method name
        Request request1 =
            new Request(
                idGenerator.generateID(),
                ecRecoverMethod+"y",
                EcRecoverParamsConverter.encode(new EcRecoverParams(helloByteMessage, signedMessage)),
                VersionType.Version2);

        RPCTestUtils.assertFails(() -> RPCTestUtils.executeRequest(request1, rpc, AddressConverter::decode), MethodNotFoundRPCException.class);

        // incorrect params
        Request request2 = new Request(idGenerator.generateID(), ecRecoverMethod, ParamUnion.wrap(new VoidParams()).encode(), VersionType.Version2);

        RPCTestUtils.assertFails(() -> RPCTestUtils.executeRequest(request2, rpc, AddressConverter::decode), InvalidParamsRPCException.class);
    }

    @Test
    public void testIsExecutable(){
        assertTrue(rpc.isExecutable(ecRecoverMethod));
        assertFalse(rpc.isExecutable("notRPC"));
    }

    @Test
    public void testPersonal_newAccount(){
        AionAddress expectedAddress = new AionAddress(ByteUtil.hexStringToBytes("a07913c03686c9659c1b614d098fd1db380a52b71fd58526b53d8107f7b355d5"));
        String password = "password";
        doReturn(expectedAddress).when(chainHolder).newAccount(anyString());

        Request request = new Request(idGenerator.generateID(), newAccountMethod,
            PasswordParamsConverter.encode(new PasswordParams(password)), VersionType.Version2);

        AionAddress responseAddress = RPCTestUtils.executeRequest(request, rpc,AddressConverter::decode);
        assertEquals(expectedAddress, responseAddress);
    }

    @Test
    public void testPersonal_unlockAccount(){
        AionAddress address = new AionAddress(ByteUtil.hexStringToBytes("a07913c03686c9659c1b614d098fd1db380a52b71fd58526b53d8107f7b355d5"));
        String password0 = "password";
        String password1 = "password1";
        int timeout0 = 300;
        Integer timeout1 = null;

        UnlockAccountParams params0 = new UnlockAccountParams(address, password0, timeout0);
        UnlockAccountParams params1 = new UnlockAccountParams(address, password1, timeout1);
        assertNotNull(params1.duration);// check that the rpc library creates a default value for the duration

        doReturn(false).when(chainHolder).unlockAccount(eq(address), eq(password0), anyInt());
        doReturn(true).when(chainHolder).unlockAccount(eq(address), eq(password1), anyInt());

        assertFalse(RPCTestUtils.executeRequest(new Request(idGenerator.generateID(), unlockAccountMethod,
            UnlockAccountParamsConverter.encode(params0), VersionType.Version2), rpc,BoolConverter::decode));
        assertTrue(RPCTestUtils.executeRequest(new Request(idGenerator.generateID(), unlockAccountMethod,
            UnlockAccountParamsConverter.encode(params1), VersionType.Version2), rpc, BoolConverter::decode));
    }

    @Test
    public void testPersonal_lockAccount(){
        AionAddress address = new AionAddress(ByteUtil.hexStringToBytes("a07913c03686c9659c1b614d098fd1db380a52b71fd58526b53d8107f7b355d5"));
        String password0 = "password";
        String password1 = "password1";

        LockAccountParams params0 = new LockAccountParams(address, password0);
        LockAccountParams params1 = new LockAccountParams(address, password1);

        doReturn(false).when(chainHolder).lockAccount(eq(address), eq(password0));
        doReturn(true).when(chainHolder).lockAccount(eq(address), eq(password1));

        assertFalse(RPCTestUtils.executeRequest(new Request(idGenerator.generateID(), lockAccountMethod,
            LockAccountParamsConverter.encode(params0), VersionType.Version2), rpc,BoolConverter::decode));
        assertTrue(RPCTestUtils.executeRequest(new Request(idGenerator.generateID(), lockAccountMethod,
            LockAccountParamsConverter.encode(params1), VersionType.Version2), rpc,BoolConverter::decode));
    }

    @Test
    public void testPersonal_ListAccounts(){
        List<AionAddress> address = new ArrayList<>();
        AccountManager accountManager = new AccountManager(AionLoggerFactory.getLogger(LogEnum.API.name()));
        int addressCount = 3;
        for (int i = 0; i<addressCount; i++){
            address.add(accountManager.createAccount("password"));
        }
        chainHolder= spy(new AionChainHolder(AionImpl.instForTest(), accountManager));
        doCallRealMethod().when(chainHolder).listAccounts();
        rpc = spy(new RPCMethods(chainHolder));
        doCallRealMethod().when(rpc).personal_listAccounts();
        Request request = new Request(idGenerator.generateID(), listAccountMethod, null, VersionType.Version2);
        AionAddress[] aionAddressList = RPCTestUtils.executeRequest(request, rpc,AddressListConverter::decode);

        verify(chainHolder, atLeastOnce()).listAccounts();
        assertEquals(addressCount, aionAddressList.length);
        assertEquals(Set.copyOf(address), Set.of(aionAddressList)); // check that we get all
                                                                        // the expected addresses
    }
}
