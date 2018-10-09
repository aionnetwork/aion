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
package org.aion.api.server;

import static junit.framework.TestCase.assertEquals;
import java.math.BigInteger;
import java.util.List;
import org.aion.api.server.types.TxRecptLg;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Assert;
import org.junit.Test;
import org.aion.base.type.Address;



public class TxRecptLgTest {

/*
pragma solidity ^0.4.0;

contract A{
    event AE(uint128 value);
    event AEA(uint128 value);
    event AEB(uint128 value);

    function AA(address addr){
        AE(1);
        AEA(2);
        //B(addr).BB(addr, 1, 256, 128)
        addr.call.value(0)(bytes4(sha3("BB(address,uint128,bytes32,bytes32)")), addr, 1, 256, 128);
        AEB(3);
    }
}

contract B{
    event BE(address indexed _recipient, uint128 indexed _amount, bytes32 indexed _foreignNetworkId, bytes32 _foreignData);

    function BB(address _recipient, uint128 _amount, bytes32 _foreignNetworkId, bytes32 _foreignData){
        BE(_recipient, _amount, _foreignNetworkId, _foreignData);
    }
}
*/

    @Test
    public void TestTxRecptLg() throws InterruptedException {
        System.out.println("run TestTxRecptLg.");

        String contractA = "0x605060405234156100105760006000fd5b610015565b610210806100246000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680639dacb3a3146100335761002d565b60006000fd5b341561003f5760006000fd5b61005d6004808080601001359035909160200190919290505061005f565b005b7fcd6e704bef76e0fe8cfd711b498592ac8e681ff2aaace78726cbb1caea41249660016040518082815260100191505060405180910390a17f5e14984659fb7f7b895bdd08828f5de80edca21fa3f8d58bac20f83fc95a246e60026040518082815260100191505060405180910390a18181600060405180806f424228616464726573732c75696e743181526010016f32382c627974657333322c627974657381526010016f333229000000000000000000000000008152601001506023019050604051809103902090506c010000000000000000000000009004908585600161010060806040518763ffffffff166c01000000000000000000000000028152600401808686825281601001526020018460ff1681526010018361ffff1681526010018260ff168152601001955050505050506000604051808303818589895af1945050505050507fd8d8edb52a0e15005d291f5cb67640400ee21833034b9f09c4afad215fcffffe60036040518082815260100191505060405180910390a15b50505600a165627a7a72305820ac4d58f9a803c3144f57ca227ff9dbd6e121d52a10c070a22dc447ae219b97660029";
        String contractB = "0x605060405234156100105760006000fd5b610015565b61011f806100246000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680637fe7a5a014603157602b565b60006000fd5b3415603c5760006000fd5b608f60048080806010013590359091602001909192908035906010019091908080601001359035906000191690909160200190919290808060100135903590600019169090916020019091929050506091565b005b83839060001916908660008a8a7ff2433e32c57456caf7380aef3887a965ac2021deb7bf0989ee41a2cf4387b2f88989604051808383906000191690906000191690825281601001526020019250505060405180910390a45b505050505050505600a165627a7a723058209ec9a5d885689d4f729e93521792f26ebb06e8bc0d4b245489b602ddb289a1000029";

        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        //======================
        // DEPLOY contract A & B
        //======================
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx1 = new AionTransaction(
                nonce.toByteArray(),
                null,
                new byte[0],
                ByteUtil.hexStringToBytes(contractA),
                1_000_000L,
                1L
        );
        tx1.sign(deployerAccount);

        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx2 = new AionTransaction(
                nonce.toByteArray(),
                null,
                new byte[0],
                ByteUtil.hexStringToBytes(contractB),
                1_000_000L,
                1L
        );
        tx2.sign(deployerAccount);

        BlockContext context = bc.createNewBlockContext(bc.getBestBlock(), List.of(tx1, tx2), false);
        ImportResult result = bc.tryToConnect(context.block);
        assertEquals(result, ImportResult.IMPORTED_BEST);

        Address addressA = tx1.getContractAddress();
        System.out.println("contract A = " + addressA);
        Address addressB = tx2.getContractAddress();
        System.out.println("contract B = " + addressB);
        Thread.sleep(1000);

        //======================
        // CALL function A.AA
        //======================
        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx3 = new AionTransaction(
                nonce.toByteArray(),
                addressA,
                new byte[0],
                ByteUtil.merge(ByteUtil.hexStringToBytes("0x9dacb3a3"), addressB.toBytes(), new DataWord(80_000).getData()),
                1_000_000L,
                1L
        );
        tx3.sign(deployerAccount);

        context = bc.createNewBlockContext(bc.getBestBlock(), List.of(tx3), false);
        result = bc.tryToConnect(context.block);
        assertEquals(result, ImportResult.IMPORTED_BEST);

        AionTxInfo info = bc.getTransactionInfo(tx3.getHash());
        AionTxReceipt receipt = info.getReceipt();
        System.out.println(receipt);
        Assert.assertEquals(4, receipt.getLogInfoList().size());
        Thread.sleep(1000);

        //======================
        //  Test
        //======================
        TxRecptLg[] logs = new TxRecptLg[receipt.getLogInfoList().size()];
        for (int i = 0; i < logs.length; i++) {
            Log logInfo = receipt.getLogInfoList().get(i);
            logs[i] = new TxRecptLg(logInfo, context.block, info.getIndex(), receipt.getTransaction(), i, true);
        }
        String ctAddrA = "0x" + addressA.toString();
        String ctAddrB = "0x" + addressB.toString();

        assertEquals(ctAddrA, logs[0].address); // AE
        assertEquals(ctAddrA, logs[1].address); // AEA
        assertEquals(ctAddrB, logs[2].address); // b.BB
        assertEquals(ctAddrA, logs[3].address); // AEB
    }
}
