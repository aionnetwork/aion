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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.*;

import com.google.common.primitives.Longs;
import org.aion.base.db.*;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.types.AionBlock;


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
    private Address activeDomainsAddressName = Address.wrap("0000000000000000000000000000000000000000000000000000000000000602");
    private Address activeDomainsAddressValue = Address.wrap("0000000000000000000000000000000000000000000000000000000000000603");

    private Address auctionDomainsAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000700");
    private Address auctionDomainsAddressName = Address.wrap("0000000000000000000000000000000000000000000000000000000000000702");

    private Address allAddresses = Address.wrap("0000000000000000000000000000000000000000000000000000000000000800");
    private Address domainNameAddressPair = Address.wrap("0000000000000000000000000000000000000000000000000000000000000801");
    private Address domainAddressNamePair = Address.wrap("0000000000000000000000000000000000000000000000000000000000000802");

    private final Address address;
    private final static long COST = 20000L;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private static final int AUCTION_TIME = 2 * 1000;
    private static final int ACTIVE_TIME = 4 * 1000;

    private static final String BID_KEY_COUNTER = "bidKeyCounterKey";
    private static final String BID_KEY_ADDR_F = "bidderAddressKeyF";
    private static final String BID_KEY_ADDR_S = "bidderAddressKeyS";
    private static final String BID_KEY_VALUE = "bidValueKey";
    private static final String ALL_ADDR_KEY = "allAddressKey";
    private static final String ALL_ADDR_COUNTER_KEY = "allAddressKey";

    private Timer timer = new Timer();
    private AionBlock block;

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

    // get the current blocktime of the blockchain,
    private static long getBlockTime(){
        //final int NUMBER_OF_BLOCKS = 6;
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        ImportResult result = ImportResult.IMPORTED_BEST;
        int counter = 0;

        while (result.isBest()){
            result = chain.tryToConnect(chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true));
            System.out.println(result + "   " + chain.getBestBlock().getNumber());
            AionBlock blk = chain.getRepository().getBlockStore().getBestBlock();
            System.out.println(blk.getNumber() + " " + blk.getTimestamp());
            counter++;
        }

        return chain.getBestBlock().getTimestamp();

    }

    private static IRepositoryConfig repoConfig =
            new IRepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "";
                }

                @Override
                public IPruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public IContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };


    /**
     * Call this function to put a bid in the domain address
     *
     * The input parameter of this method is a byte array whose bytes should be supplied in the
     * following expected format:
     *
     * [1b domainNameLength] length of the byte[] containing the domain name
     * [mb domainName] the domain name to bid
     * [32b bidderAddress] address of the bidder, must be the same as the address from the constructor
     * [96b signature] signature of the bidder
     * [1b balanceLength] the length of the byte[] containing the bid value
     * [nb balance] where n > 0
     *
     * 1 + m + 32 + 96 + 1 + n > 132
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST)
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);
        if(input.length <= 132)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // sort and store the input data
        int offset = 0;
        offset++;
        int domainNameLength = input[0];
        int balanceLength = input[129 + domainNameLength];

        byte[] bidderAddressInByte = new byte[ADDR_LEN];
        byte[] sign = new byte[SIG_LEN];
        byte[] balance = new byte[balanceLength];

        byte[] domainNameInBytes = new byte[domainNameLength];
        String domainName;
        Address domainAddress;

        System.arraycopy(input, offset, domainNameInBytes, 0, domainNameLength);
        offset = offset + domainNameLength;
        System.arraycopy(input, offset, bidderAddressInByte, 0, ADDR_LEN);
        offset = offset + ADDR_LEN;
        System.arraycopy(input, offset, sign, 0,SIG_LEN);
        offset = offset + SIG_LEN;
        offset ++;
        System.arraycopy(input, offset, balance, 0, balanceLength);

        String rawDomainName = new String(domainNameInBytes);
        // check if the domain name is valid to register
        if (!isValidDomainName(rawDomainName))
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // add zeros for storing
        byte[] domainNameInBytesWithZeros = addLeadingZeros(domainNameInBytes);
        domainName =  new String(domainNameInBytesWithZeros);
        // remove the last part (.aion) before storing
        domainName = domainName.substring(0, 32);

        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        Address bidderAddress = Address.wrap(bidderAddressInByte);
        BigInteger bidValue = new BigInteger(balance);

        // check if bidValue is valid (greater than 0)
        if (bidValue.compareTo(new BigInteger("0")) < 0)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // check bidder addr and its balance
        if(this.track.hasAccountState(bidderAddress)){
            if (this.track.getAccountState(bidderAddress).getBalance().compareTo(bidValue) < 0){
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "insufficient balance".getBytes());
            }
        }

        else
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "bidder account does not exist".getBytes());

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

        // if this domain name does not have an address
        if (this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(domainName.getBytes()))).equals(DataWord.ZERO)){
            domainAddress = createAddressForDomain(domainName);
        }
        else{ // extract the address corresponding to the domain name
            domainAddress = getAddressFromName(domainName);
        }

        // if this domain is already active
        if (isActiveDomain(domainAddress)) {
            return new ContractExecutionResult(ResultCode.FAILURE, nrg - COST, domainAddress.toBytes());
        }

        // if this domain is already in auction state
        else if (isAuctionDomain(domainAddress)){
            processBid(domainAddress, bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, domainAddress.toBytes());
        }

        // start the auction for the given domain
        else{
            storeNewAddress(domainAddress);
            addToAuctionDomain(domainAddress, domainName);
            processBid(domainAddress, bidderAddress, bidValue);
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - COST, domainAddress.toBytes());
        }
    }

    /**
     * Store the given domain address in the collection of domains in auction
     * process. Set a new task, scheduling the auction to run for 3 days and
     * then completed.
     *
     * @param domainAddress address of the domain bid for
     */
    private void addToAuctionDomain(Address domainAddress, String domainName){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + AUCTION_TIME); // 3 days later, 3s
        //byte[] date = Longs.toByteArray(finishDate.getTime());
        //this.track.startTracking();
        //IRepositoryCache db = track.startTracking();
        //AionBlockchainImpl blockchain = (AionBlockchainImpl) this.track.startTracking();
        //long t = blk.getTimestamp();

        long dateInLong =  finishDate.getTime();
        String dateString = String.valueOf(dateInLong);
        byte[] date;

        try {
            date = dateString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            date = Longs.toByteArray(finishDate.getTime());
            System.out.println("Could not resolve date properly");
            e.printStackTrace();
        }

        this.track.addStorageRow(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), new DataWord(fillByteArray(date)));
        this.track.addStorageRow(auctionDomainsAddressName, new DataWord(blake128(domainAddress.toBytes())), new DataWord(domainName.substring(0, 16).getBytes()));
        this.track.addStorageRow(auctionDomainsAddressName, new DataWord(blake128(blake128(domainAddress.toBytes()))), new DataWord(domainName.substring(16, 32).getBytes()));

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
    private void addToActiveDomains(Address domainAddress, Address ownerAddress, byte[] domainName1, byte[] domainName2, BigInteger value){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + ACTIVE_TIME); // 1 year later, 5s
        //byte[] date = Longs.toByteArray(finishDate.getTime());
        long dateInLong =  finishDate.getTime();
        String dateString = String.valueOf(dateInLong);
        byte[] date;

        try {
            date = dateString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            date = Longs.toByteArray(finishDate.getTime());
            System.out.println("Could not resolve date properly");
            e.printStackTrace();
        }

        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(ownerAddress.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(ownerAddress.toBytes(), 16, addrSecondPart, 0, 16);
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())),new DataWord(addrFirstPart));
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(blake128(domainAddress.toBytes()))),new DataWord(addrSecondPart));

        this.track.addStorageRow(activeDomainsAddressTime, new DataWord(blake128(domainAddress.toBytes())), new DataWord(fillByteArray(date)));
        this.track.addStorageRow(activeDomainsAddressName, new DataWord(blake128(domainAddress.toBytes())), new DataWord(domainName1));
        this.track.addStorageRow(activeDomainsAddressName, new DataWord(blake128(blake128(domainAddress.toBytes()))), new DataWord(domainName2));

        this.track.addStorageRow(activeDomainsAddressValue, new DataWord(blake128(domainAddress.toBytes())), new DataWord(value));

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

        this.track.getAccountState(bidderAddress).subFromBalance(value);

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

        byte[] domainNameFirstHalf;
        byte[] domainNameSecondHalf;
        byte[] domainNameCombined;
        String domainName;

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
                // return the smaller amount to account
                if (tempAmount.compareTo(highestBid) > 0) {
                    this.track.addBalance(tempAddress, highestBid);
                    highestBid = tempAmount;
                }
                else{
                    this.track.addBalance(tempAddress, tempAmount);
                }
            }

            // if temp address is different from winner address
            else{
                if (tempAmount.compareTo(highestBid) > 0){
                    //return previous winner and amount to its bidder
                    this.track.addBalance(winnerAddress, highestBid);
                    //set winner variables
                    secondHighestBid = highestBid;
                    highestBid = tempAmount;
                    winnerAddress = tempAddress;
                }
                else if (tempAmount.compareTo(secondHighestBid) > 0){
                    this.track.addBalance(tempAddress, tempAmount);
                    secondHighestBid = tempAmount;
                }
                else{
                    this.track.addBalance(tempAddress, tempAmount);
                }
            }
            // erase it after
            this.track.addStorageRow(domainAddress, new DataWord(bidderKeyVal), new DataWord(new BigInteger("0")));
        }
        // return difference between the top 2 bids to winner
        this.track.addBalance(winnerAddress, highestBid.subtract(secondHighestBid));

        //get the domain name
        domainNameFirstHalf = this.track.getStorageValue(auctionDomainsAddressName, new DataWord(blake128(domainAddress.toBytes()))).getData();
        domainNameSecondHalf = this.track.getStorageValue(auctionDomainsAddressName, new DataWord(blake128(blake128(domainAddress.toBytes())))).getData();
        domainNameCombined = combineTwoBytes(domainNameFirstHalf, domainNameSecondHalf);
        byte[] trimmedDomainName = trimLeadingZeros(domainNameCombined);
        domainName = new String (trimmedDomainName);

        this.track.addStorageRow(domainAddress, new DataWord(counterHash), new DataWord(new BigInteger("0")));
        // remove from auction domains
        this.track.addStorageRow(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), new DataWord(0));
        addToActiveDomains(domainAddress, winnerAddress, domainNameFirstHalf, domainNameSecondHalf, secondHighestBid);
        printWinner(domainAddress, winnerAddress, secondHighestBid, domainName);
    }

    /**
     * insert (16 - length of input) 0s to the beginning of the byte, used to
     * store into Dataword.
     *
     * @param inputBytes the byte[] to be filled to 16 length
     * @return the filled byte array
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
     * @return the trimmed byte array
     */
    private boolean isActiveDomain(Address domainAddress){
        DataWord key = new DataWord(blake128(domainAddress.toBytes()));
        return !(this.track.getStorageValue(activeDomainsAddress,key).equals(DataWord.ZERO));
    }

    /**
     * Checks if the given domain is in auction process
     *
     * @param domainAddress a domain address
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
    private void printWinner (Address domainAddress, Address winnerAddress, BigInteger value, String domainName){
        System.out.println("Auction result for domain at: '" + domainAddress + "'");
        System.out.println("    Domain name: " + domainName + ".aion");
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
        // return deposit
        byte[] addrFirstPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes()))).getData();
        byte[] addrSecondPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(blake128(domainAddress.toBytes())))).getData();
        Address ownerAddress = Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));

        byte[] valueData = this.track.getStorageValue(activeDomainsAddressValue, new DataWord(blake128(domainAddress.toBytes()))).getData();
        BigInteger tempValue = new BigInteger(valueData);

        this.track.addBalance(ownerAddress, tempValue);
        printRemoveActiveDomain(domainAddress);
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
        this.track.addStorageRow(activeDomainsAddressName, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
        this.track.addStorageRow(activeDomainsAddressName, new DataWord(blake128(blake128(domainAddress.toBytes()))), DataWord.ZERO);
        this.track.addStorageRow(activeDomainsAddressValue, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
        this.track.addStorageRow(activeDomainsAddressTime, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
    }

    /**
     * Checks if the domain name follows the rules:
     *      - between 8-37 characters in length (including the .aion at the end)
     *      - follows the ascii base code
     *      - end with .aion
     *      - are at max 4 levels including .aion head ("a.a.a.aion" is valid while a.a.a.a.aion is invalid"
     *
     * @param domainName name of the domain
     */
    private boolean isValidDomainName(String domainName){
        if (domainName.length() < 8 || domainName.length() > 37)
            return false;
        if (!domainName.matches("[a-zA-Z0-9.]*"))
            return false;
        String[] domainPartitioned = domainName.split("\\.");
        if (!domainPartitioned[domainPartitioned.length - 1].equals("aion"))
            return false;
        return domainPartitioned.length <= 4;
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
     * Create an aion address for the given domain name and store it
     *
     * @param domainName name of the domain
     * @return address generated for  the domain
     */
    private Address createAddressForDomain(String domainName){
        ECKey domainAddr = ECKeyFac.inst().create();
        Address domainAddress = Address.wrap(domainAddr.getAddress());

        // setup for storing data
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(domainAddress.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(domainAddress.toBytes(), 16, addrSecondPart, 0, 16);
        byte[] nameFirstPart = domainName.substring(0,16).getBytes();
        byte[] nameSecondPart =domainName.substring(16,32).getBytes();

        // store address -> name pair
        this.track.addStorageRow(domainNameAddressPair, new DataWord(blake128(domainName.getBytes())), new DataWord(addrFirstPart));
        this.track.addStorageRow(domainNameAddressPair, new DataWord(blake128(blake128(domainName.getBytes()))), new DataWord(addrSecondPart));

        // store name -> address pair
        this.track.addStorageRow(domainAddressNamePair, new DataWord(blake128(domainAddress.toBytes())), new DataWord(nameFirstPart));
        this.track.addStorageRow(domainAddressNamePair, new DataWord(blake128(blake128(domainAddress.toBytes()))), new DataWord(nameSecondPart));

        return domainAddress;
    }

    /**
     * Get the corresponding address for the given domain name
     * @param domainName name of domain
     * @return address of domain
     */
    private Address getAddressFromName(String domainName){
        byte[] addrFirstPart = this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(domainName.getBytes()))).getData();
        byte[] addrSecondPart = this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(blake128(domainName.getBytes())))).getData();
        return Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    /**
     * Stores the domain address into a collection of all domain addresses
     * also increment the count by 1
     *
     * @param domainAddress address of domain
     */
    private void storeNewAddress(Address domainAddress){
        // get the number of domains already in the set
        IDataWord counterData = this.track.getStorageValue(allAddresses, new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())));
        BigInteger counter = new BigInteger(counterData.getData());

        // setup for storage
        DataWord newKey1 = new DataWord(blake128((ALL_ADDR_KEY + counter).getBytes()));
        DataWord newKey2 = new DataWord(blake128(blake128((ALL_ADDR_KEY + counter).getBytes())));
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(domainAddress.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(domainAddress.toBytes(), 16, addrSecondPart, 0, 16);

        // store the new generated domain address to a collection of all the domain addresses
        this.track.addStorageRow(allAddresses, newKey1, new DataWord(addrFirstPart));
        this.track.addStorageRow(allAddresses, newKey2, new DataWord(addrSecondPart));
        this.track.addStorageRow(allAddresses, new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())), new DataWord(counter.add(BigInteger.ONE)));
    }

    //tasks
    class finishAuction extends TimerTask {
        Address domainAddress;

        finishAuction(Address input){
            domainAddress = input;
        }

        @Override
        public void run() {
            System.out.println("--------------------------PERFORMING TASK: finishAuction--------------------------");
            processAuction(domainAddress);
        }
    }

    class removeActiveDomain extends TimerTask {
        Address domainAddress;

        removeActiveDomain(Address input) {
            domainAddress = input;
        }

        @Override
        public void run() {
            System.out.println("--------------------------PERFORMING TASK: removeActiveDomain--------------------------");
            removeActiveDomain(domainAddress);
        }
    }

    // helpers
    private byte[] trimLeadingZeros(byte[] b) {
        if (b == null) return null;

        int counter = 0;
        for (int i = 0; i < 32; i++) {
            if (b[i] != 0)
                break;
            counter++;
        }

        byte[] ret = new byte[32 - counter];
        System.arraycopy(b, counter, ret, 0, 32 - counter);
        return ret;
    }

    private byte[] trimLeadingZeros16(byte[] b) {
        if (b == null) return null;

        int counter = 0;
        for (int i = 0; i < 16; i++) {
            if (b[i] != 0)
                break;
            counter++;
        }

        byte[] ret = new byte[16 - counter];
        System.arraycopy(b, counter, ret, 0, 16 - counter);
        return ret;
    }

    private byte[] addLeadingZeros(byte[] b) {
        byte[] ret = new byte[37];
        System.arraycopy(b, 0, ret, 37 - b.length, b.length);
        return ret;
    }

    private List<ActiveDomainsData> getAllActiveDomains() {
        IDataWord numberOfDomainsTotalData = this.track.getStorageValue(allAddresses, new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())));
        BigInteger numberOfDomainsTotal = new BigInteger(numberOfDomainsTotalData.getData());

        int counter = numberOfDomainsTotal.intValue();
        List<ActiveDomainsData> actives = new ArrayList<>();

        for (int i = 0; i < counter; i++){
            byte[] firstHash = blake128((ALL_ADDR_KEY + i).getBytes());
            byte[] secondHash = blake128(blake128((ALL_ADDR_KEY + i).getBytes()));
            byte[] addrFirstPart = this.track.getStorageValue(allAddresses, new DataWord(firstHash)).getData();
            byte[] addrSecondPart = this.track.getStorageValue(allAddresses, new DataWord(secondHash)).getData();
            Address tempDomainAddr = Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));

            // if domain exists
            if(!this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).equals(DataWord.ZERO)) {
                byte[] ownerAddrFirstPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                byte[] ownerAddrSecondPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(blake128(tempDomainAddr.toBytes())))).getData();
                Address tempOwnerAddr = Address.wrap(combineTwoBytes(ownerAddrFirstPart, ownerAddrSecondPart));

                byte[] expireDateData = this.track.getStorageValue(activeDomainsAddressTime, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                byte[] trimmedExpireDateData = trimLeadingZeros16(expireDateData);
                String expireDateStr = null;
                try {
                    expireDateStr = new String(trimmedExpireDateData, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    expireDateStr = "";
                }
                Date tempExpireDate = new Date(Long.parseLong(expireDateStr));

                byte[] domainNameFirstPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                byte[] domainNameSecondPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(blake128(tempDomainAddr.toBytes())))).getData();
                String tempDomainName = null;
                try {
                    tempDomainName = new String(trimLeadingZeros(combineTwoBytes(domainNameFirstPart, domainNameSecondPart)), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    tempDomainName = "";
                }
                tempDomainName = tempDomainName + ".aion";

                byte[] valueData = this.track.getStorageValue(activeDomainsAddressValue, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                BigInteger tempValue = new BigInteger(valueData);

                ActiveDomainsData tempData = new ActiveDomainsData(tempDomainName, tempDomainAddr, tempOwnerAddr, tempExpireDate, tempValue);
                actives.add(tempData);
            }
        }
        return actives;
    }

    private List<AuctionDomainsData> getAllAuctionDomains(){
        IDataWord numberOfDomainsTotalData = this.track.getStorageValue(allAddresses, new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())));
        BigInteger numberOfDomainsTotal = new BigInteger(numberOfDomainsTotalData.getData());

        int counter = numberOfDomainsTotal.intValue();
        List<AuctionDomainsData> auctions = new ArrayList<>();
        for (int i = 0; i < counter; i++){
            byte[] firstHash = blake128((ALL_ADDR_KEY + i).getBytes());
            byte[] secondHash = blake128(blake128((ALL_ADDR_KEY + i).getBytes()));
            byte[] addrFirstPart = this.track.getStorageValue(allAddresses, new DataWord(firstHash)).getData();
            byte[] addrSecondPart = this.track.getStorageValue(allAddresses, new DataWord(secondHash)).getData();

            Address tempDomainAddr = Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));

            // if domain exists
            if(!this.track.getStorageValue(auctionDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).equals(DataWord.ZERO)){
                byte[] expireDateData = this.track.getStorageValue(auctionDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                byte[] trimmedExpireDateData = trimLeadingZeros16(expireDateData);
                String expireDateStr = null;
                try {
                    expireDateStr = new String(trimmedExpireDateData, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    expireDateStr = "";
                }
                Date tempExpireDate = new Date(Long.parseLong(expireDateStr));

                byte[] domainNameFirstPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(tempDomainAddr.toBytes()))).getData();
                byte[] domainNameSecondPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(blake128(tempDomainAddr.toBytes())))).getData();
                String tempDomainName = null;
                try {
                    tempDomainName = new String(trimLeadingZeros(combineTwoBytes(domainNameFirstPart, domainNameSecondPart)), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    tempDomainName = "";
                }
                tempDomainName = tempDomainName + ".aion";

                byte[] numBidsData = this.track.getStorageValue(tempDomainAddr, new DataWord(blake128(BID_KEY_COUNTER.getBytes()))).getData();
                BigInteger tempNumberOfBids = new BigInteger(numBidsData);

                AuctionDomainsData tempData = new AuctionDomainsData(tempDomainName, tempDomainAddr, tempExpireDate, tempNumberOfBids);
                auctions.add(tempData);
            }
        }
        return  auctions;
    }

    private HashMap<Address, BigInteger> getBidsForADomain(Address domainAddress){
        HashMap<Address, BigInteger> bids = new HashMap<>();

        byte[] numBidsData = this.track.getStorageValue(domainAddress, new DataWord(blake128(BID_KEY_COUNTER.getBytes()))).getData();
        BigInteger numberOfBids = new BigInteger(numBidsData);
        byte[] bidderKey1, bidderKey2, bidderKeyVal;

        for (int i = 0; i < numberOfBids.intValue(); i++){
            bidderKey1 = blake128((BID_KEY_ADDR_F + i).getBytes());
            bidderKey2 = blake128((BID_KEY_ADDR_S + i).getBytes());
            bidderKeyVal = blake128((BID_KEY_VALUE + i).getBytes());

            IDataWord addr1 = this.track.getStorageValue(domainAddress, new DataWord(bidderKey1));
            IDataWord addr2 = this.track.getStorageValue(domainAddress, new DataWord(bidderKey2));
            Address bidderAddr = new Address(combineTwoBytes(addr1.getData(), addr2.getData()));
            IDataWord amountData = this.track.getStorageValue(domainAddress, new DataWord(bidderKeyVal));
            BigInteger bidAmount = new BigInteger(amountData.getData());

            // check if there is multiple bids from same address
            if (bids.containsKey(bidderAddr)){
                if(bidAmount.compareTo(bids.get(bidderAddr)) > 0)
                    bids.put(bidderAddr, bidAmount);
            }
            else {
                bids.put(bidderAddr, bidAmount);
            }
        }

        return bids;
    }

    /**
     * Query Functions
     */
    public void displayAllActiveDomains() {
        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();

        System.out.println("--------------------------AION NAME SERVICE QUERY: Active Domains (" + activeDomainsList.size() + ")-----------------------------");
        for (ActiveDomainsData domain: activeDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Owner address: " + domain.ownerAddress);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.expireDate);
            System.out.println("    Value: " + domain.auctionValue);
        }
        System.out.println();
    }

    public void displayAllAuctionDomains(){
        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();

        System.out.println("--------------------------AION NAME SERVICE QUERY: Auction Domains (" + auctionDomainsList.size() + ")-----------------------------");
        for (AuctionDomainsData domain: auctionDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.completeDate);
            System.out.println("    Number of bids for this domain: " + domain.numberOfBids);
        }
        System.out.println();
    }

    public void displayAllDomains(){
        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();
        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();

        System.out.println("-----------------------------AION NAME SERVICE QUERY: displayAllDomains----------------------------");
        System.out.println("ACTIVE DOMAINS(" + activeDomainsList.size()+ "): ");
        for (ActiveDomainsData domain: activeDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Owner address: " + domain.ownerAddress);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.expireDate);
            System.out.println("    Value: " + domain.auctionValue);
        }
        System.out.println();

        System.out.println("AUCTION DOMAINS(" + auctionDomainsList.size()+ "): ");
        for (AuctionDomainsData domain: auctionDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.completeDate);
            System.out.println("    Number of bids for this domain: " + domain.numberOfBids);
        }
        System.out.println();
    }

    public void displayMyDomains(ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());
        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();
        System.out.println("----------------------------AION NAME SERVICE QUERY: displayMyDomains-----------------------------");
        System.out.println("DOMAINS FOR ACCOUNT: " + callerAddress.toString());
        for(ActiveDomainsData domain: activeDomainsList){
            if(domain.ownerAddress.equals(callerAddress)){
                System.out.println("Domain name: " + domain.domainName);
                System.out.println("    Owner address: " + domain.ownerAddress);
                System.out.println("    Domain address: " + domain.domainAddress);
                System.out.println("    Expire Date: " + domain.expireDate);
                System.out.println("    Value: " + domain.auctionValue);
            }
        }
    }

    public void displayMyBids(ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());

        System.out.println("-----------------------------AION NAME SERVICE QUERY: displayAllMyBids----------------------------");

        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();
        for (AuctionDomainsData domain: auctionDomainsList){
            Address tempDomainAddress = domain.domainAddress;
            HashMap<Address, BigInteger> bids = getBidsForADomain(tempDomainAddress);
            if(bids.containsKey(callerAddress)){
                System.out.println("Domain name: " + domain.domainName );
                System.out.println("    Your bid value: " + bids.get(callerAddress).intValue());
            }
        }
        System.out.println();
    }

    public void displayMyBidForDomain(String domainNameRaw, ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());

        byte[] domainNameInBytes = domainNameRaw.substring(0, domainNameRaw.length() - 5).getBytes();

        String domainName2 = new String(addLeadingZeros(domainNameInBytes));
        String domainName = domainName2.substring(5, 37);
        byte[] addrFirstPart, addrSecondPart;

        addrFirstPart = this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(domainName.getBytes()))).getData();
        addrSecondPart = this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(blake128(domainName.getBytes())))).getData();
        Address domainAddress = Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));

        HashMap<Address, BigInteger> bids = getBidsForADomain(domainAddress);

        System.out.println("--------------------------AION NAME SERVICE QUERY: displayMyBidForDomain--------------------------");
        System.out.println("Domain name: " + domainNameRaw);

        if(bids.containsKey(callerAddress))
            System.out.println("    Your bid value: " + bids.get(this.address));

        else
            System.out.println("    You do not have a bid for domain: " + domainAddress);
        System.out.println();
    }

    // data structures used to store pass data for query
    class ActiveDomainsData{
        String domainName;
        Address domainAddress;
        Address ownerAddress;
        Date expireDate;
        BigInteger auctionValue;

        ActiveDomainsData(String domainName, Address domainAddress, Address ownerAddress, Date expireDate, BigInteger auctionValue){
            this.domainName = domainName;
            this.domainAddress = domainAddress;
            this.ownerAddress = ownerAddress;
            this.expireDate = expireDate;
            this.auctionValue = auctionValue;
        }
    }

    class AuctionDomainsData{
        String domainName;
        Address domainAddress;
        Date completeDate;
        BigInteger numberOfBids;

        AuctionDomainsData(String domainName, Address domainAddress, Date completeDate, BigInteger numberOfBids){
            this.domainName = domainName;
            this.domainAddress = domainAddress;
            this.completeDate = completeDate;
            this.numberOfBids = numberOfBids;
        }
    }
}

