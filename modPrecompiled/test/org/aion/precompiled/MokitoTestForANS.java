/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.precompiled;

import org.aion.base.db.*;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;

import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.contracts.AionAuctionContract;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.AionNameServiceContract;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Properties;
import org.mockito.Mockito;
import org.mockito.Mock;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;

public class MokitoTestForANS {
    private Address domainAddress1 = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 = Address.wrap("a022222222222222222222222222222202020202020202020202020202020202");
    private String domainName1 = "bion.aion";
    private String domainName2 = "cion.aion.aion";
    private IRepositoryCache repo;
    private AionAuctionContract testAAC;

    private ECKey defaultKey;
    private BigInteger defaultBidAmount = new BigInteger("1000");
    private long DEFAULT_INPUT_NRG = 24000;
    private ECKey k;
    private ECKey k2;
    private ECKey k3;
    private ECKey k4;

    AionNameServiceContract aionNameServiceContract = Mockito.mock(AionNameServiceContract.class);

    @Before
    public void setup() {
        repo = new DummyRepo();
        defaultKey = ECKeyFac.inst().create();
        //testAAC = new AionAuctionContract(repo, domainAddress1);
        repo.createAccount(Address.wrap(defaultKey.getAddress()));
        repo.addBalance(Address.wrap(defaultKey.getAddress()), new BigInteger("4000000"));

        k = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();
        k4 = ECKeyFac.inst().create();
        repo.createAccount(Address.wrap(k.getAddress()));
        repo.createAccount(Address.wrap(k2.getAddress()));
        repo.createAccount(Address.wrap(k3.getAddress()));
        repo.createAccount(Address.wrap(k4.getAddress()));
        repo.addBalance(Address.wrap(k.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k2.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k3.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k4.getAddress()), new BigInteger("10000"));


    }

    @Test
    public void testMain(){
        when(aionNameServiceContract.getOwnerAddress()).thenReturn(domainAddress1);
    }



    private byte[] setupInputs(String domainName, Address ownerAddress, byte[] amount, ECKey k){
        int domainLength = domainName.length();
        int amountLength = amount.length;
        int offset = 0;
        byte[] ret = new byte[1 + domainLength + 32 + 96 + 1 + amountLength];

        ISignature signature = k.sign(ownerAddress.toBytes());

        System.arraycopy(new byte[]{(byte)domainLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(domainName.getBytes(), 0, ret, offset, domainLength);
        offset = offset + domainLength;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[]{(byte)amountLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amountLength);

        return ret;
    }

}
