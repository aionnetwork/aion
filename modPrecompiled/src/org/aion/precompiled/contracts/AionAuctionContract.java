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

package org.aion.precompiled.contracts;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;

import ch.qos.logback.core.subst.Token;
import javafx.util.Pair;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;


/**
 * Aion Auction Contract
 *
 * This contract is a registrar to registrate aion domains.
 *
 * @author William
 */

public class AionAuctionContract extends StatefulPrecompiledContract {
    private final static long COST = 20000L;

    private Address address;
    private Address ownerAddress;

    private Timer timer = new Timer();
    private TimerTask task = new finishAuction();

    // Map<domain address, time left>
    // domainAddresses are hashes of the domain names
    private static Map<Address, Date> activeDomains = new HashMap<>(); // Map<domainAddress, ExpireDate>
    private static Map<Address, Date> auctionDomains = new HashMap<>(); // Map<domainAddress, ExpireDate>
    private static Map<Address, Map<Address, BigInteger>> bids = new HashMap<>(); // Map<domainAddress, Map<bidderAddress, Amount>>

    private static Map<Address, Address> activeDomainsOwners = new HashMap<>(); // Map<domainAddress, domainOwnerAddress>

    public AionAuctionContract(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address address, Address ownerAddress) {
        super(track);
        this.address = address;
        this.ownerAddress = ownerAddress;
    }

    /**
     *
     * input is defined as:
     *
     * [32b domainAddress]
     * [32b bidderAddress]
     * [1b length of balance]
     * [nb balance] where n > 0
     *
     * 32 + 32 + 1 + n > 65
     *
     */

    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST)
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);
        if(input.length <= 65)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        int offset = 0;
        byte[] domainAddressInByte = new byte[32];
        byte[] bidderAddressInByte = new byte[32];
        int balanceLength = input[64];
        byte[] balance = new byte[balanceLength];

        System.arraycopy(input, offset, domainAddressInByte, 0, 32);
        offset = offset + 32;
        System.arraycopy(input, offset, bidderAddressInByte, 0, 32);
        offset = offset + 32;
        offset ++;
        System.arraycopy(input, offset, balance, 0, balanceLength);

        Address domainAddress = Address.wrap(domainAddressInByte);
        Address bidderAddress = Address.wrap(bidderAddressInByte);
        BigInteger bidValue = new BigInteger(balance);

        // if domainAddress is already active
        if(AionAuctionContract.activeDomains.containsKey(domainAddress))
            return new ContractExecutionResult(ResultCode.FAILURE, nrg - COST);

        // if domainAddress is already in auction process
        else if(AionAuctionContract.auctionDomains.containsKey(domainAddress)){
            // put the bid in
            AionAuctionContract.bids.get(domainAddress).put(bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST);
        }

        // the domainAddress is not in auction process, begin it
        else{
            addAuctionDomain(domainAddress);
            // put the bid in
            Map<Address, BigInteger> emptyMap = new HashMap<>();
            AionAuctionContract.bids.put(domainAddress, emptyMap);
            AionAuctionContract.bids.get(domainAddress).put(bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST);
        }
    }

    /**
     * Helper Functions
     */

    private void addAuctionDomain(Address domainAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + 5 * 1000L); // 1 day later, 5s

        AionAuctionContract.auctionDomains.put(domainAddress, finishDate);
        timer.schedule(task, 5000L);
    }

    private void addToActiveDomains(Address domainAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + 60 * 1000L); // 1 year later, 60s

        AionAuctionContract.activeDomains.put(domainAddress, finishDate);

        Timer timer = new Timer();
        TimerTask task = new removeActiveDomain();
        timer.schedule(task, finishDate);
    }

    private void domainTimeUp(Address domainAddress){
        AionAuctionContract.activeDomains.remove(domainAddress);
    }

    private void getHighestBidder(){


    }

    private void removeActiveDomain(){
    }


    class finishAuction extends TimerTask {
        @Override
        public void run() {
            System.out.println("performing task: finishAuction");
            getHighestBidder();
        }
    }

    class removeActiveDomain extends TimerTask{
        @Override
        public void run() {
            System.out.println("performing task: removeActiveDomain");
            removeActiveDomain();
        }
    }


}
