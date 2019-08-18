// == TemplatedDeserializer.java == 
package org.aion.api.server.rpc2.autogen;

import com.fasterxml.jackson.databind.JsonNode;
import org.aion.api.schema.NamedRpcType;
import org.aion.api.schema.SchemaValidationException;
import org.aion.api.schema.TypeRegistry;
import org.aion.api.serialization.RpcTypeDeserializer;
import org.aion.api.server.rpc2.autogen.pod.Transaction;
import org.aion.api.server.rpc2.autogen.pod.CallRequest;
import org.aion.api.server.rpc2.autogen.pod.SomeStruct;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public class TemplatedSerializer extends RpcTypeDeserializer {
    @Override
    public Object deserializeObject(JsonNode value,
                                    NamedRpcType type) throws SchemaValidationException {
        switch(type.getName()) {
            case "Transaction":
                return new Transaction(
                    (byte[]) super.deserialize(
                        value.get("blockHash"),
                        (NamedRpcType) type.getContainedFields().get(0).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("blockNumber"),
                        (NamedRpcType) type.getContainedFields().get(1).getType()
                    ),
                    (byte[]) super.deserialize(
                        value.get("from"),
                        (NamedRpcType) type.getContainedFields().get(2).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("nrg"),
                        (NamedRpcType) type.getContainedFields().get(3).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("nrgPrice"),
                        (NamedRpcType) type.getContainedFields().get(4).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("gas"),
                        (NamedRpcType) type.getContainedFields().get(5).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("gasPrice"),
                        (NamedRpcType) type.getContainedFields().get(6).getType()
                    ),
                    (byte[]) super.deserialize(
                        value.get("hash"),
                        (NamedRpcType) type.getContainedFields().get(7).getType()
                    ),
                    (byte[]) super.deserialize(
                        value.get("input"),
                        (NamedRpcType) type.getContainedFields().get(8).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("nonce"),
                        (NamedRpcType) type.getContainedFields().get(9).getType()
                    ),
                    (byte[]) super.deserialize(
                        value.get("to"),
                        (NamedRpcType) type.getContainedFields().get(10).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("transactionIndex"),
                        (NamedRpcType) type.getContainedFields().get(11).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("value"),
                        (NamedRpcType) type.getContainedFields().get(12).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("timestamp"),
                        (NamedRpcType) type.getContainedFields().get(13).getType()
                    )
                );
            case "CallRequest":
                return new CallRequest(
                    (byte[]) super.deserialize(
                        value.get("to"),
                        (NamedRpcType) type.getContainedFields().get(0).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("value"),
                        (NamedRpcType) type.getContainedFields().get(1).getType()
                    ),
                    (byte[]) super.deserialize(
                        value.get("data"),
                        (NamedRpcType) type.getContainedFields().get(2).getType()
                    )
                );
            case "SomeStruct":
                return new SomeStruct(
                    (byte[]) super.deserialize(
                        value.get("MyData"),
                        (NamedRpcType) type.getContainedFields().get(0).getType()
                    ),
                    (java.math.BigInteger) super.deserialize(
                        value.get("MyQuantity"),
                        (NamedRpcType) type.getContainedFields().get(1).getType()
                    )
                );
            default:
                throw new UnsupportedOperationException(
                    "Don't know how to handle this kind of object");
        }
    }
}
