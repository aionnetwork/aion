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
import com.google.common.primitives.Longs;
import org.aion.base.db.*;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import static org.aion.crypto.HashUtil.blake128;

/**
 * The AionAuctionContract is used to register domain names. Accounts can start and bid in an
 * auction for each domain name. Auctions begin when a user puts in the first bid and the auction
 * will last for 72 hours. After the auction period, the results are calculate. The user with the
 * highest bid value will deposit the amount of the second highest bid, and become the owner of
 * the domain for a 1 year period.
 *
 * @author William
 */

public class AionAuctionContract extends StatefulPrecompiledContract {
    private Address activeDomainsAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000600");
    private Address activeDomainsAddressTime = Address.wrap("0000000000000000000000000000000000000000000000000000000000000601");
    private Address auctionDomainsAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000700");

    private final Address address;
    private final static long COST = 20000L;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private static final int AUCTION_TIME = 5 * 1000;
    private static final int ACTIVE_TIME = 10 * 1000;

    private static final String BID_KEY_COUNTER = "bidKeyCounterKey";
    private static final String BID_KEY_ADDR_F = "bidderAddressKeyF";
    private static final String BID_KEY_ADDR_S = "bidderAddressKeyS";
    private static final String BID_KEY_VALUE = "bidValueKey";

    private Timer timer = new Timer();

    /**
     * Constructs a Aion Auction Contract object, ready to execute.
     *
     * @param track The repository
     * @param address The address of the calling account
     */
    public AionAuctionContract(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address address) {
        super(track);
        this.address = address;
    }

    /**
     * Call this function to put a bid in the domain address
     *
     * The input parameter of this method is a byte array whose bytes should be supplied in the
     * following expected format:
     *
     * [32b domainAddress] address of the domain to bid
     * [32b bidderAddress] address of the bidder, must be the same as the address from the constructor
     * [96b signature] signature of the bidder
     * [1b length] the length of the bytes containing the bid value
     * [nb balance] where n > 0
     *
     * 32 + 32 + 96 + 1 + n > 161
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST)
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);
        if(input.length <= 161)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // sort and store the input data
        int offset = 0;
        byte[] domainAddressInByte = new byte[ADDR_LEN];
        byte[] bidderAddressInByte = new byte[ADDR_LEN];
        byte[] sign = new byte[SIG_LEN];
        int balanceLength = input[160];
        byte[] balance = new byte[balanceLength];

        System.arraycopy(input, offset, domainAddressInByte, 0, ADDR_LEN);
        offset = offset + ADDR_LEN;
        System.arraycopy(input, offset, bidderAddressInByte, 0, ADDR_LEN);
        offset = offset + ADDR_LEN;
        System.arraycopy(input, offset, sign, 0,SIG_LEN);
        offset = offset + SIG_LEN;
        offset ++;
        System.arraycopy(input, offset, balance, 0, balanceLength);

        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        Address domainAddress = Address.wrap(domainAddressInByte);
        Address bidderAddress = Address.wrap(bidderAddressInByte);
        BigInteger bidValue = new BigInteger(balance);

        // check if bidValue is valid (greater than 0)
        if (bidValue.compareTo(new BigInteger("0")) < 0)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // user should have the signature signed with its address
        byte[] data = new byte[ADDR_LEN];
        System.arraycopy(bidderAddressInByte, 0, data, 0, ADDR_LEN);
        boolean b = ECKeyEd25519.verify(data, sig.getSignature(), sig.getPubkey(null));

        // verify public key matches owner
        if (!b) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);
        }

        if (!bidderAddress.equals(Address.wrap(sig.getAddress()))) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);
        }

        // if this domain is already active
        if (isActiveDomain(domainAddress)) {
            return new ContractExecutionResult(ResultCode.FAILURE, nrg - COST);
        }

        // if this domain is already in auction state
        else if (isAuctionDomain(domainAddress)){
            processBid(domainAddress, bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST);
        }

        // start the auction for the given domain
        else{
            addToAuctionDomain(domainAddress);
            processBid(domainAddress, bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST);
        }
    }

    /**
     * Store the given domain address in the collection of domains in auction
     * process. Set a new task, scheduling the auction to run for 3 days and
     * then completed.
     *
     * @param domainAddress address of the domain bid for
     */
    private void addToAuctionDomain(Address domainAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + AUCTION_TIME); // 3 days later, 5s
        byte[] date = Longs.toByteArray(finishDate.getTime());

        this.track.addStorageRow(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), new DataWord(fillByteArray(date)));

        TimerTask auctionTask = new finishAuction(domainAddress);
        timer.schedule(auctionTask, finishDate);
    }


    /**
     * Store the given domain address in the collection of active domains. Set
     * a new task, scheduling the domain to be active for a period of 1 year.
     *
     * @param domainAddress address of the domain to be added
     * @param ownerAddress new owner of the given domain
     */
    private void addToActiveDomains(Address domainAddress, Address ownerAddress){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + ACTIVE_TIME); // 1 year later, 10s
        byte[] date = Longs.toByteArray(finishDate.getTime());

        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())),new DataWord(blake128(ownerAddress.toBytes())));
        this.track.addStorageRow(activeDomainsAddressTime, new DataWord(blake128(domainAddress.toBytes())), new DataWord(fillByteArray(date)));

        TimerTask removeActiveDomainTask = new removeActiveDomain(domainAddress);
        timer.schedule(removeActiveDomainTask, finishDate);
    }

    /**
     * Process the given bid. Increment the number of bids counter of the
     * domain and call function to store.
     *
     * @param domainAddress domain to bid for
     * @param bidderAddress address of the bidder
     * @param value the bid value
     */
    private void processBid(Address domainAddress, Address bidderAddress, BigInteger value){

        byte[] counterHash = blake128(BID_KEY_COUNTER.getBytes());

        IDataWord numberOfBidsData = this.track.getStorageValue(domainAddress, new DataWord(counterHash));
        BigInteger numberOfBids = new BigInteger(numberOfBidsData.getData());

        addBidToRepo(domainAddress, numberOfBids.intValue(), bidderAddress, value);

        numberOfBids = numberOfBids.add(BigInteger.valueOf(1));
        this.track.addStorageRow(domainAddress, new DataWord(counterHash), new DataWord(numberOfBids));
    }

    /**
     * Process the auction at the given domain address and find:
     *      - bidder with the highest big (Address)
     *      - second highest bid value (BigInteger)
     * Record information in repository and clean up auction values in repo.
     *
     * @param domainAddress The domain address of the auction to be processed
     */
    private void processAuction(Address domainAddress){
        byte[] counterHash = blake128(BID_KEY_COUNTER.getBytes());
        IDataWord numberOfBidsData = this.track.getStorageValue(domainAddress, new DataWord(counterHash));
        BigInteger numberOfBids = new BigInteger(numberOfBidsData.getData());

        // if there are no bids, cancel the auction, no one wins, this should never happen
        // since a first bid is needed to begin an auction
        if (numberOfBids.intValue() < 1)
            return;

        Address winnerAddress = null;
        BigInteger highestBid = new BigInteger("0");
        BigInteger secondHighestBid = new BigInteger("0");

        Address tempAddress;
        byte[] bidderKey1;
        byte[] bidderKey2;
        byte[] bidderKeyVal;
        BigInteger tempAmount;

        for (int i = 0; i < numberOfBids.intValue(); i++){
            bidderKey1 = blake128((BID_KEY_ADDR_F + i).getBytes());
            bidderKey2 = blake128((BID_KEY_ADDR_S + i).getBytes());
            bidderKeyVal = blake128((BID_KEY_VALUE + i).getBytes());

            IDataWord addr1 = this.track.getStorageValue(domainAddress, new DataWord(bidderKey1));
            IDataWord addr2 = this.track.getStorageValue(domainAddress, new DataWord(bidderKey2));
            tempAddress = new Address(combineTwoBytes(addr1.getData(), addr2.getData()));
            IDataWord data1 = this.track.getStorageValue(domainAddress, new DataWord(bidderKeyVal));
            tempAmount = new BigInteger(data1.getData());

            // if current winner and temp are same person, only update the highest bid
            if (winnerAddress != null && Arrays.equals(tempAddress.toBytes(), winnerAddress.toBytes())){
                if (tempAmount.compareTo(highestBid) > 0)
                    highestBid = tempAmount;
            }

            // if temp address is different from winner address
            else{
                if (tempAmount.compareTo(highestBid) > 0){
                    secondHighestBid = highestBid;
                    highestBid = tempAmount;
                    winnerAddress = tempAddress;
                }
                else if (tempAmount.compareTo(secondHighestBid) > 0){
                    secondHighestBid = tempAmount;
                }
            }
            // erase it after
            this.track.addStorageRow(domainAddress, new DataWord(bidderKeyVal), new DataWord(new BigInteger("0")));
        }
        this.track.addStorageRow(domainAddress, new DataWord(counterHash), new DataWord(new BigInteger("0")));
        // remove from auction domains
        this.track.addStorageRow(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), new DataWord(0));
        addToActiveDomains(domainAddress, winnerAddress);
        printWinner(domainAddress, winnerAddress, secondHighestBid);
    }

    /**
     * insert (16 - length of input) 0s to the beginning of the byte, used to
     * store into Dataword.
     *
     * @param inputBytes the byte[] to be filled to 16 length
     * @return
     */
    private byte[] fillByteArray(byte[] inputBytes){
        byte[] ret = new byte[16];
        int length = inputBytes.length;
        System.arraycopy(inputBytes, 0, ret, (16 - length), length);
        return ret;
    }

    /**
     * Checks if the domain given is an active domain.
     *
     * @param domainAddress a domain address
     * @return
     */
    private boolean isActiveDomain(Address domainAddress){
        DataWord key = new DataWord(blake128(domainAddress.toBytes()));
        return !(this.track.getStorageValue(activeDomainsAddress,key).equals(DataWord.ZERO));
    }

    /**
     * Checks if the given domain is in auction process
     *
     * @param domainAddress a domain address
     * @return
     */
    private boolean isAuctionDomain(Address domainAddress){
        DataWord key = new DataWord(blake128(domainAddress.toBytes()));
        IDataWord ret = this.track.getStorageValue(auctionDomainsAddress, key);
        return !ret.equals(DataWord.ZERO);
    }

    /**
     * Store the given bid in the repository under the given domain address.
     * Stores bidder address and the bid value with corresponding hash key.
     *
     * @param domainAddress domain of the bid
     * @param offset index for storage key
     * @param bidderAddress address of the bidder
     * @param value the bid value
     */
    private void addBidToRepo(Address domainAddress, int offset, Address bidderAddress, BigInteger value) {
        byte[] bidderKey1 = blake128((BID_KEY_ADDR_F + offset).getBytes());
        byte[] bidderKey2 = blake128((BID_KEY_ADDR_S + offset).getBytes());
        byte[] bidderKeyVal = blake128((BID_KEY_VALUE + offset).getBytes());

        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];

        System.arraycopy(bidderAddress.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(bidderAddress.toBytes(), 16, addrSecondPart, 0, 16);

        this.track.addStorageRow(domainAddress, new DataWord(bidderKey1), new DataWord(addrFirstPart));
        this.track.addStorageRow(domainAddress, new DataWord(bidderKey2), new DataWord(addrSecondPart));
        this.track.addStorageRow(domainAddress, new DataWord(bidderKeyVal), new DataWord(value));
    }

    /**
     * Prints out information for the auction of the given domain.
     *
     * @param domainAddress address of the domain
     * @param winnerAddress address of the auction winner (new owner of domain)
     * @param value the value (second highest) to deposit
     */
    private void printWinner (Address domainAddress, Address winnerAddress, BigInteger value){
        System.out.println("Auction result for domain at: '" + domainAddress + "'");
        System.out.println("    New domain owner: " + winnerAddress);
        Date terminateDate = new Date();
        System.out.println("    Auction complete date: " + terminateDate.toString());
        System.out.println("    Value: " + value.toString());
        System.out.println();
    }

    /**
     * Print out information for removing an active domain
     *
     * @param domainAddress address of the domain
     */
    private void printRemoveActiveDomain(Address domainAddress){
        System.out.println("Removing active domain at: " + domainAddress);
        Date terminateDate = new Date();
        System.out.println("    Terminate time at: " + terminateDate.toString());
        System.out.println();
    }

    /**
     * Removes the given domain from the collection of active domains in repo.
     *
     * @param domainAddress address of the domain
     */
    private void removeActiveDomain(Address domainAddress){
        printRemoveActiveDomain(domainAddress);
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
    }

    /**
     * Combines two length 16 byte[] (usually retrieved from repo as a
     * DataWord(length 16) into a length 32 byte[].
     *
     * @param byte1 input1
     * @param byte2 input2
     * @return the combined length 32 byte[]
     */
    private byte[] combineTwoBytes(byte[] byte1, byte[] byte2) {
        byte[] combined = new byte[32];
        System.arraycopy(byte1, 0, combined, 0, 16);
        System.arraycopy(byte2, 0, combined, 16, 16);
        return combined;
    }

    /**
     * tasks
     */
    class finishAuction extends TimerTask {
        Address domainAddress;

        public finishAuction(Address input){
            domainAddress = input;
        }

        @Override
        public void run() {
            System.out.println("                                PERFORMING TASK: finishAuction");
            processAuction(domainAddress);
        }
    }

    class removeActiveDomain extends TimerTask {
        Address domainAddress;

        public removeActiveDomain(Address input) {
            domainAddress = input;
        }

        @Override
        public void run() {
            System.out.println("                                PERFORMING TASK: removeActiveDomain");
            removeActiveDomain(domainAddress);
        }
    }
}
