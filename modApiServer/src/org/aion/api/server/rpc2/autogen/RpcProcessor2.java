// === RpcProcessor2.java ===
/******************************************************************************
 *
 * AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
 * BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
 *
 *****************************************************************************/

package org.aion.api.server.rpc2.autogen;
import org.aion.api.server.rpc2.AbstractRpcProcessor;
import org.aion.api.serialization.JsonRpcRequest;

public class RpcProcessor2 extends AbstractRpcProcessor {
    private final Rpc rpc;

    public RpcProcessor2(Rpc rpc) {
        this.rpc = rpc;
    }

    public Object execute(JsonRpcRequest req) throws Exception {
        Object[] params = req.getParams();
        switch(req.getMethod()) {
            case "getseed":
                return (byte[]) rpc.getseed(
                );
            case "submitseed":
                return (byte[]) rpc.submitseed(
                    (byte[]) params[0],
                    (byte[]) params[1]
                );
            case "submitsignature":
                return (boolean) rpc.submitsignature(
                    (byte[]) params[0],
                    (byte[]) params[1]
                );
            default: throw new UnsupportedOperationException("Not a valid method.");
        }
    }
}
