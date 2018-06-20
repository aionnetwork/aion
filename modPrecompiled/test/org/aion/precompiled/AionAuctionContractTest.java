package org.aion.precompiled;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.ds.ArchivedDataSource;
import org.aion.precompiled.contracts.AionAuctionContract;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Date;
import java.util.Map;
import java.util.Collections.*;

import static org.aion.crypto.HashUtil.blake128;

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
        byte[] combined = setupInputs(domainAddress1, Address.wrap(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract((IRepositoryCache) repo, domainAddress1);
        aac.execute(combined, inputEnergy);

        BigInteger amount2 = new BigInteger("5000");
        byte[] combined2 = setupInputs(domainAddress1, Address.wrap(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1);
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 = setupInputs(domainAddress1, Address.wrap(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1);
        aac3.execute(combined3, inputEnergy);

        BigInteger amount4 = new BigInteger("5000");
        byte[] combined4 = setupInputs(domainAddress1, Address.wrap(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1);
        aac4.execute(combined4, inputEnergy);

        BigInteger amount5 = new BigInteger("2000");
        byte[] combined5 = setupInputs(domainAddress1, Address.wrap(k5.getAddress()), amount5.toByteArray(), k5);
        AionAuctionContract aac5 = new AionAuctionContract((IRepositoryCache) repo, domainAddress1);
        aac5.execute(combined5, inputEnergy);

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BigInteger amount6 = new BigInteger("2000");
        byte[] combined6 = setupInputs(domainAddress2, Address.wrap(k.getAddress()), amount6.toByteArray(), k);
        AionAuctionContract aac6 = new AionAuctionContract((IRepositoryCache) repo, domainAddress2);
        aac6.execute(combined6, inputEnergy);

        BigInteger amount7 = new BigInteger("4000");
        byte[] combined7 = setupInputs(domainAddress2, Address.wrap(k2.getAddress()), amount7.toByteArray(), k2);
        AionAuctionContract aac7 = new AionAuctionContract((IRepositoryCache) repo, domainAddress2);
        aac7.execute(combined7, inputEnergy);

        try {
            Thread.sleep(10 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testInvalid(){
        ECKey k = new ECKeyEd25519();
        ECKey k2 = new ECKeyEd25519();

        DummyRepo repo = new DummyRepo();
        AionAuctionContract aac = new AionAuctionContract((IRepositoryCache)repo, domainAddress1);
        AionAuctionContract aac2 = new AionAuctionContract((IRepositoryCache)repo, domainAddress2);

        String aaa = "aaaasadsaf";

        byte[] a = blake128(aaa.getBytes());
        byte[] b = blake128(aaa.getBytes());
        byte[] c = blake128(aaa.getBytes());

        for(int i=0;i<a.length;i++){
            System.out.print(a[i]);
        }
        System.out.println();

        for(int i=0;i<a.length;i++){
            System.out.print(b[i]);
        }
        System.out.println();

        for(int i=0;i<a.length;i++){
            System.out.print(c[i]);
        }
    }

    private byte[] setupInputs(Address domainAddress, Address ownerAddress, byte[] amount, ECKey k){
        byte[] ret = new byte[32 + 32 + 96 + 1 + amount.length];
        int offset = 0;

        ISignature signature = k.sign(ownerAddress.toBytes());

        System.arraycopy(domainAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[]{(byte)amount.length}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amount.length);

        return ret;
    }
}
