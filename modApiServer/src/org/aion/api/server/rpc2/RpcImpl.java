package org.aion.api.server.rpc2;

import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.util.bytes.ByteUtil;

public class RpcImpl implements Rpc {

    /* Test impls only to check RpcProcessor2 works properly */

    @Override
    public byte[] getseed() {
        return ByteUtil.hexStringToBytes("0xc0ffee000000000000000000000000000000000000000000000000000000cafec0ffee000000000000000000000000000000000000000000000000000000cafe");
    }

    @Override
    public byte[] submitseed(byte[] var0, byte[] var1, byte[] var2) {
        return var1;
    }

    @Override
    public boolean submitsignature(byte[] var0, byte[] var1) {
        return false; //TODO
    }

    // Commenting out the impls of the demo methods for now (avoid having unused stuff in master)

    /*

    @Override
    public Transaction eth_getTransactionByHash2(byte[] var0) {
        AionTxInfo txInfo = AionImpl.inst().aionHub.getBlockchain().getTransactionInfo(var0);
        if(txInfo == null) {
            return null;
        }

        Block b = AionImpl.inst().aionHub.getBlockchain().getBlockByHash(txInfo.getBlockHash());
        if (b == null) {
            throw new RuntimeException("This is an internal error.");
        }

        return new Transaction(
                txInfo.getBlockHash(),
                BigInteger.valueOf(b.getNumber()),
                txInfo.getReceipt().getTransaction().getSenderAddress().toByteArray(),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyLimit()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyPrice()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyLimit()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyPrice()),
                txInfo.getReceipt().getTransaction().getTransactionHash(),
                txInfo.getReceipt().getTransaction().getData(),
                txInfo.getReceipt().getTransaction().getNonceBI(),
                txInfo.getReceipt().getTransaction().getDestinationAddress().toByteArray(),
                BigInteger.valueOf(txInfo.getIndex()),
                txInfo.getReceipt().getTransaction().getValueBI(),
                BigInteger.valueOf(b.getTimestamp())
        );
    }

    @Override
    public byte[] eth_call2(CallRequest var0) {
        AionTransaction tx =
                AionTransaction.createWithoutKey(
                        BigInteger.valueOf(0).toByteArray(),
                        AddressUtils.ZERO_ADDRESS,
                        AddressUtils.wrapAddress(ByteUtil.toHexString(var0.getTo())),
                        var0.getValue().toByteArray(),
                        var0.getData(),
                        CfgAion.inst().getApi().getNrg().getNrgPriceDefault(),
                        Long.MAX_VALUE,
                        (byte)1
                );
        return AionImpl
                .inst()
                .callConstant(
                        tx,
                        AionImpl.inst().getBlockchain().getBestBlock()
                ).getTransactionOutput();
    }

    @Override
    public byte[] eth_sendTransaction2(CallRequest var0) throws UnauthorizedRpcException {
        throw new UnauthorizedRpcException("Account needs to be unlocked.");
    }

    */
}
