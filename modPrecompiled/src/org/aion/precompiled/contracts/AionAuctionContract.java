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
import java.util.*;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
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

    private Timer timer2 = new Timer();
    private TimerTask task2 = new removeActiveDomain();

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
            addToAuctionDomain(domainAddress);
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

    private void addToAuctionDomain(Address domainAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + 5 * 1000L); // 1 day later, 5s

        AionAuctionContract.auctionDomains.put(domainAddress, finishDate);
        timer.schedule(task, 5000L);
    }

    private void addToActiveDomains(Address domainAddress, Address ownerAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + 20 * 1000L); // 1 year later, 20s

        AionAuctionContract.activeDomains.put(domainAddress, finishDate);
        AionAuctionContract.activeDomainsOwners.put(domainAddress, ownerAddress);
        timer2.schedule(task2, finishDate);
    }

    private void removeActiveDomain(){
        Address domainAddress = findEarliestActiveDomain();
        processActiveDomain(domainAddress);
    }

    // go through the activeDomains, and find the one that is finished, return the domain address
    private Address findEarliestActiveDomain(){
        Address domainAddress = null;
        Date earliestDate = null;
        for (Map.Entry<Address, Date> domainName : AionAuctionContract.activeDomains.entrySet()) {
            Address addr = domainName.getKey();
            Date date = domainName.getValue();

            // first iteration
            if (Objects.equals(earliestDate, null)) {
                earliestDate = date;
                domainAddress = addr;
            }

            else if (date.getTime() < earliestDate.getTime()) {
                earliestDate = date;
                domainAddress = addr;
            }
        }
        return domainAddress;
    }

    private void processActiveDomain(Address domainAddress){
        AionAuctionContract.activeDomains.remove(domainAddress);
        AionAuctionContract.activeDomainsOwners.remove(domainAddress);

        //display result
        System.out.println("Removed domain: " + domainAddress + " from active domains\n");
    }




    private void finishAuction (){
        Address domainAddress = findEarliestAuctionDomain();
        processAuctionWinner(domainAddress);
    }

    // go through the auctionDomains, and find the one that is finished, return the domain address
    private Address findEarliestAuctionDomain(){
        Address domainAddress = null;
        Date earliestDate = null;
        for (Map.Entry<Address, Date> domainName : AionAuctionContract.auctionDomains.entrySet()) {
            Address addr = domainName.getKey();
            Date date = domainName.getValue();

            // first iteration
            if (Objects.equals(earliestDate, null)) {
                earliestDate = date;
                domainAddress = addr;
            }

            else if (date.getTime() < earliestDate.getTime()) {
                earliestDate = date;
                domainAddress = addr;
            }
        }
        return domainAddress;
    }

    // find the highest bid (address) and second highest bid (value)
    private void processAuctionWinner(Address domainAddress) {
        Address winnerAddress = null;
        BigInteger highestBid = new BigInteger("0");
        BigInteger secondHighestBid = new BigInteger("0");

        // iterate through the bids
        for (Map.Entry<Address, BigInteger> bidder : AionAuctionContract.bids.get(domainAddress).entrySet()) {
            Address addr = bidder.getKey();
            BigInteger amount = bidder.getValue();

            if(amount.compareTo(highestBid) > 0){
                secondHighestBid = highestBid;
                highestBid = amount;
                winnerAddress = addr;
            }

            else if(amount.compareTo(secondHighestBid) > 0){
                secondHighestBid = amount;
            }
        }

        // display result
        printWinner(domainAddress, winnerAddress, secondHighestBid);

        // clean up other maps
        AionAuctionContract.auctionDomains.remove(domainAddress);
        AionAuctionContract.bids.remove(domainAddress);

        // put into active domains
        addToActiveDomains(domainAddress, winnerAddress);
    }

    private void printWinner (Address domainAddress, Address winnerAddress, BigInteger value){
        System.out.println("Auction result for domain at: '" + domainAddress + "'");
        System.out.println("    New domain owner: " + winnerAddress);
        System.out.println("    Value: " + value.toString());
        System.out.println();
    }




    class finishAuction extends TimerTask {
        @Override
        public void run() {
            System.out.println("                                PERFORMING TASK: finishAuction");
            finishAuction();
        }
    }

    class removeActiveDomain extends TimerTask{
        @Override
        public void run() {
            System.out.println("                                PERFORMING TASK: removeActiveDomain");
            removeActiveDomain();
        }
    }

    public static void printAuctions(){
        System.out.println("                                CURRENT DOMAINS REPOSITORY ");
        System.out.println("Active domains:");
        for (Map.Entry<Address, Date> domainName : AionAuctionContract.activeDomains.entrySet()) {
            System.out.println("    domainAddress: " + domainName.getKey() + "      timeToLive: "
                    + domainName.getValue() + "    domainOwner: " + AionAuctionContract.activeDomainsOwners.get(domainName.getKey()));
        }

        System.out.println("Domains in auction:");
        for (Map.Entry<Address, Date> domainName : AionAuctionContract.auctionDomains.entrySet()) {
            System.out.println("    domainAddress: " + domainName.getKey() + "      timeToLive: " + domainName.getValue());
        }

        System.out.println("Auction domains bids:");
        for (Map.Entry<Address, Map<Address, BigInteger>> domainName : AionAuctionContract.bids.entrySet()) {
            System.out.println("    domainAddress: " + domainName.getKey());
            for (Map.Entry<Address, BigInteger> dName : AionAuctionContract.bids.get(domainName.getKey()).entrySet()) {
                System.out.println("        bidderAddress: " + dName.getKey() + "      amount: " + dName.getValue());
            }
        }

        System.out.println();
    }

}
