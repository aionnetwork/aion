package org.aion.precompiled;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.ds.ArchivedDataSource;
import org.aion.precompiled.contracts.AionAuctionContract;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Date;
import java.util.Map;

public class AionAuctionContractTest {

    private Address domainAddress1 = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 = Address.wrap("a022222222222222222222222222222202020202020202020202020202020202");

    @Test
    public void testMain(){
        final long inputEnergy = 24000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        ECKey k4 = ECKeyFac.inst().create();
        ECKey k5 = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();

        BigInteger amount = new BigInteger("1000");
        byte[] combined = setupInputs(domainAddress1, Address.wrap(k.getAddress()), amount.toByteArray());
        AionAuctionContract aac = new AionAuctionContract((IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        aac.execute(combined, inputEnergy);

        BigInteger amount2 = new BigInteger("5000");
        byte[] combined2 = setupInputs(domainAddress1, Address.wrap(k2.getAddress()), amount2.toByteArray());
        AionAuctionContract aac2 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1, Address.wrap(k2.getAddress()));
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 = setupInputs(domainAddress1, Address.wrap(k3.getAddress()), amount3.toByteArray());
        AionAuctionContract aac3 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1, Address.wrap(k3.getAddress()));
        aac3.execute(combined3, inputEnergy);

        BigInteger amount4 = new BigInteger("6000");
        byte[] combined4 = setupInputs(domainAddress1, Address.wrap(k4.getAddress()), amount4.toByteArray());
        AionAuctionContract aac4 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1, Address.wrap(k4.getAddress()));
        aac4.execute(combined4, inputEnergy);

        BigInteger amount5 = new BigInteger("2000");
        byte[] combined5 = setupInputs(domainAddress1, Address.wrap(k5.getAddress()), amount5.toByteArray());
        AionAuctionContract aac5 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1, Address.wrap(k5.getAddress()));
        aac5.execute(combined5, inputEnergy);

        try {
            Thread.sleep(4 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BigInteger amount6 = new BigInteger("2000");
        byte[] combined6 = setupInputs(domainAddress2, Address.wrap(k.getAddress()), amount6.toByteArray());
        AionAuctionContract aac6 = new AionAuctionContract((IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));
        aac6.execute(combined6, inputEnergy);

        BigInteger amount7 = new BigInteger("4000");
        byte[] combined7 = setupInputs(domainAddress2, Address.wrap(k2.getAddress()), amount7.toByteArray());
        AionAuctionContract aac7 = new AionAuctionContract((IRepositoryCache) repo, domainAddress2, Address.wrap(k2.getAddress()));
        aac7.execute(combined7, inputEnergy);

        AionAuctionContract.printAuctions();

        try {
            Thread.sleep(7 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AionAuctionContract.printAuctions();

        try {
            Thread.sleep(22 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AionAuctionContract.printAuctions();
    }

    private byte[] setupInputs(Address domainAddress, Address ownerAddress, byte[] amount){
        byte[] ret = new byte[32 + 32 + 32];
        int offset = 0;

        System.arraycopy(domainAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(new byte[]{(byte)amount.length}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amount.length);

        return ret;
    }
}
