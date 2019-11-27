package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.function.Function;
import org.aion.rpc.client.IDGeneratorStrategy;
import org.aion.rpc.client.SimpleIDGenerator;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.PongEnum;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.PongEnumConverter;
import org.junit.Before;
import org.junit.Test;

public class UtilityRPCImplTest {
    private final IDGeneratorStrategy idGeneratorStrategy = new SimpleIDGenerator();
    private final String ping = "ping";
    private RPCServerMethods rpcServerMethods;

    @Before
    public void setup() {
        rpcServerMethods = new RPCMethods(null, null);
    }

    @Test
    public void testPing() {
        assertEquals(PongEnum.PONG,
                execute(
                        new Request(
                                idGeneratorStrategy.generateID(), ping, null, VersionType.Version2),
                        PongEnumConverter::decode));
    }

    private <T> T execute(Request request, Function<Object, T> extractor) {
        return extractor.apply(RPCServerMethods.execute(request, rpcServerMethods));
    }
}
