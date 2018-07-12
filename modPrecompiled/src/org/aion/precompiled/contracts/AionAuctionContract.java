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
import java.util.Timer;

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
 * will last for 72 hours. After the auction period, the results are calculated. The user with the
 * highest bid value will deposit the amount of the second highest bid, and become the owner of
 * the domain for a 1 year period, everyone else will get their bid value back.
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
    private static final BigInteger MINIMUM_AMOUNT =  new BigInteger("100");

    private static final String BID_KEY_COUNTER = "bidKeyCounterKey";
    private static final String BID_KEY_ADDR = "bidderAddressKey";
    private static final String BID_KEY_VALUE = "bidValueKey";
    private static final String ALL_ADDR_KEY = "allAddressKey";
    private static final String ALL_ADDR_COUNTER_KEY = "allAddressKey";

    private static Timer timer = new Timer();
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
     * For bidding, input parameter of this method is a byte array whose bytes should be supplied in the
     * following expected format:
     *
     * [1b domainNameLength] length of the byte array containing the domain name
     * [mb domainName] the domain name to bid, m > 0
     * [32b bidderAddress] address of the bidder, must be the same as the address from the constructor
     * [96b signature] signature of the bidder
     * [1b balanceLength] the length of the byte[] containing the bid value
     * [nb balance] where n > 0
     *
     * 1 + m + 32 + 96 + 1 + n = 130 + m + n
     *
     * For time extension request  on active domain, input parameter of this method should be supplied as:
     *
     * [1b domainNameLength] length of the byte array containing the domain name
     * [mb domainName] the domain to time extend, m > 0
     * [32b callerAddress] should be the owner of the domain
     * [96b signature] signature of the caller
     * [1b operator] set this to 0 for time extension
     *
     * 1 + m + 32 + 96 + 1 = 130 + m
     *
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrg) {
        if (nrg < COST)
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0, "insufficient energy".getBytes());

        // check length for both operations
        if(input.length < 131){
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect input length".getBytes());
        }

        int domainNameLength = input[0];
        if (domainNameLength < 0)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect input length".getBytes());

        // check if input is too short for extension function
        if (input.length < 130 + domainNameLength)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect input length".getBytes());
        int balanceLength = input[129 + domainNameLength];

        if(balanceLength > 0){
            if(input.length < 130 + domainNameLength + balanceLength){
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect input length".getBytes());
            }
        }

        // sort and store the input data
        byte[] bidderAddressInByte = new byte[ADDR_LEN];
        byte[] sign = new byte[SIG_LEN];
        byte[] domainNameInBytes = new byte[domainNameLength];
        String domainName;
        Address domainAddress;

        int offset = 0;
        offset++;
        System.arraycopy(input, offset, domainNameInBytes, 0, domainNameLength);
        offset = offset + domainNameLength;
        System.arraycopy(input, offset, bidderAddressInByte, 0, ADDR_LEN);
        offset = offset + ADDR_LEN;
        System.arraycopy(input, offset, sign, 0,SIG_LEN);
        offset = offset + SIG_LEN;
        offset ++;

        String rawDomainName = new String(domainNameInBytes);
        // check if the domain name is valid to register
        if (!isValidDomainName(rawDomainName))
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST);

        // add zeros for storing
        byte[] domainNameInBytesWithZeros = addLeadingZeros(domainNameInBytes);
        domainName = new String(domainNameInBytesWithZeros);
        // remove the last part (.aion) before storing
        domainName = domainName.substring(0, 32);

        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        Address bidderAddress = Address.wrap(bidderAddressInByte);

        // user should have the signature signed with its address
        byte[] data = new byte[ADDR_LEN];
        System.arraycopy(bidderAddressInByte, 0, data, 0, ADDR_LEN);
        boolean b = ECKeyEd25519.verify(data, sig.getSignature(), sig.getPubkey(null));

        // verify public key matches owner
        if (!b) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect signature".getBytes());
        }

        if (!bidderAddress.equals(Address.wrap(sig.getAddress()))) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "incorrect key".getBytes());
        }

        // if this domain name does not have an address
        if (this.track.getStorageValue(domainNameAddressPair, new DataWord(blake128(domainName.getBytes()))).equals(DataWord.ZERO)){
            domainAddress = createAddressForDomain(domainName);
        }
        else{ // extract the address corresponding to the domain name
            domainAddress = getAddressFromName(domainName);
        }


        // if request to extend time
        if (balanceLength < 1){
            return extensionRequest(domainAddress, bidderAddress, nrg);
        }


        // get the bid value
        byte[] balance = new byte[balanceLength];
        System.arraycopy(input, offset, balance, 0, balanceLength);
        BigInteger bidValue = new BigInteger(balance);

        // check if bidValue is valid (greater than 0)
        if (bidValue.compareTo(new BigInteger("0")) < 0)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "negative bid value".getBytes());

        // check bidder addr and its balance
        if(this.track.hasAccountState(bidderAddress)){
            if (this.track.getAccountState(bidderAddress).getBalance().compareTo(bidValue) < 0){
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "insufficient balance".getBytes());
            }
        }
        else
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg - COST, "bidder account does not exist".getBytes());



        // if this domain is already active
        if (isActiveDomain(domainAddress)) {
            return new ContractExecutionResult(ResultCode.FAILURE, nrg - COST, "requested domain is already active".getBytes());
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

        addDateToStorage(auctionDomainsAddress, domainAddress, finishDate);
        addNameToStorage(auctionDomainsAddressName, domainAddress, domainName);
        this.track.addStorageRow(auctionDomainsAddressName, new DataWord(blake128(domainAddress.toBytes())), new DataWord(domainName.substring(0, 16).getBytes()));
        this.track.addStorageRow(auctionDomainsAddressName, new DataWord(blake128(blake128(domainAddress.toBytes()))), new DataWord(domainName.substring(16, 32).getBytes()));

        TimerTask auctionTask = new finishAuction(domainAddress);
        timer.schedule(auctionTask, finishDate);
    }

    // record to data base, change stuff, so when its time for task to execute, it will first check database
    // to see if it has been extended, if it has, schedule new task.
    private ContractExecutionResult extensionRequest(Address domainAddress, Address ownerAddress, long nrg){
        Date expireDateFromStorage = getDateFromStorage(activeDomainsAddressTime, domainAddress);
        Date currentDate = new Date();
        long difference = expireDateFromStorage.getTime() - currentDate.getTime();

        // check if domain is currently active, but have not been extended
        if(expireDateFromStorage.getTime() < currentDate.getTime() || difference > ACTIVE_TIME) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, COST - nrg, "already been extended".getBytes());
        }

        // add the new expire date
        Date finishDate = new Date(expireDateFromStorage.getTime() + ACTIVE_TIME); //extend for 1 period
        addDateToStorage(activeDomainsAddressTime, domainAddress, finishDate);
        return new ContractExecutionResult(ResultCode.SUCCESS, COST - nrg );
    }

    /**
     * Store the given domain address in the collection of active domains. Set
     * a new task, scheduling the domain to be active for a period of 1 year.
     *
     * @param domainAddress address of the domain to be added
     * @param ownerAddress new owner of the given domain
     */
    private void addToActiveDomains(Address domainAddress, Address ownerAddress, String domainName, BigInteger value){
        Date currentDate = new Date();
        Date finishDate = new Date(currentDate.getTime() + ACTIVE_TIME); // 1 year later, 5s

        addBigIntegerToStorage(activeDomainsAddressValue, domainAddress, value);
        addDateToStorage(activeDomainsAddressTime, domainAddress, finishDate);
        addAddressToStorage(activeDomainsAddress, domainAddress, ownerAddress);
        addNameToStorage2(activeDomainsAddressName, domainAddress, domainName);

        TimerTask removeActiveDomainTask = new removeActiveDomain(domainAddress, finishDate.getTime());
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
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        addBidToRepo(domainAddress, numberOfBids.intValue(), bidderAddress, value);
        numberOfBids = numberOfBids.add(BigInteger.valueOf(1));
        addBigIntegerToStorage(domainAddress, BID_KEY_COUNTER, numberOfBids);
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
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        // if there are no bids, cancel the auction, no one wins, this should never happen
        // since a first bid is needed to begin an auction
        if (numberOfBids.intValue() < 1)
            return;

        String domainName;
        Address winnerAddress = null;
        BigInteger highestBid = MINIMUM_AMOUNT;
        BigInteger secondHighestBid = new BigInteger("0");

        Address tempAddress;
        BigInteger tempAmount;

        for (int i = 0; i < numberOfBids.intValue(); i++){
            tempAddress = getAddressFromStorage(domainAddress, BID_KEY_ADDR + i);
            tempAmount = getBigIntegerFromStorage(domainAddress, BID_KEY_VALUE + i);

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
            addBigIntegerToStorage(domainAddress, BID_KEY_VALUE + i, new BigInteger("0"));
        }
        // return difference between the top 2 bids to winner
        this.track.addBalance(winnerAddress, highestBid.subtract(secondHighestBid));

        domainName = getNameFromStorage(auctionDomainsAddressName, domainAddress);
        addBigIntegerToStorage(domainAddress, BID_KEY_COUNTER, new BigInteger("0"));
        // remove from auction domains
        this.track.addStorageRow(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), new DataWord(0));
        addToActiveDomains(domainAddress, winnerAddress, domainName, secondHighestBid);
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
        addBigIntegerToStorage(domainAddress, BID_KEY_VALUE + offset, value);
        addAddressToStorage(domainAddress, BID_KEY_ADDR + offset, bidderAddress);
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
        System.out.println("    Domain name: " + domainName);
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
    private void removeActiveDomain(Address domainAddress, long expireTime){
        // retrieve expire time from storage
        Date expireDateFromStorage = getDateFromStorage(activeDomainsAddressTime, domainAddress);

        // if domain active time has been extended, schedule new task and return
        if(expireDateFromStorage.getTime() > expireTime){
            System.out.println("    domain active time has been extended, schedule new task for : " + expireDateFromStorage + "\n");
            TimerTask removeActiveDomainTask = new removeActiveDomain(domainAddress, expireDateFromStorage.getTime());
            timer.schedule(removeActiveDomainTask, expireDateFromStorage);
            return;
        }

        // return deposit
        Address ownerAddress = getAddressFromStorage(activeDomainsAddress, domainAddress);
        BigInteger tempValue = getBigIntegerFromStorage(activeDomainsAddressValue, domainAddress);
        this.track.addBalance(ownerAddress, tempValue);

        printRemoveActiveDomain(domainAddress);
        // erase
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())), DataWord.ZERO);
        this.track.addStorageRow(activeDomainsAddress, new DataWord(blake128(blake128(domainAddress.toBytes()))), DataWord.ZERO);
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

        // store address -> name pair & name -> address pair
        addNameToStorage(domainAddressNamePair, domainAddress, domainName);
        addAddressToStorage(domainNameAddressPair, domainName, domainAddress);
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
        BigInteger counter = getBigIntegerFromStorage(allAddresses, ALL_ADDR_COUNTER_KEY);
        this.track.addStorageRow(allAddresses, new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())), new DataWord(counter.add(BigInteger.ONE)));
        addAddressToStorage(allAddresses, ALL_ADDR_KEY + counter, domainAddress);
    }

    // storage processing --------------------------------------------------------------------------------------------//
    private Address getAddressFromStorage(Address key, Address key2){
        byte[] addrFirstPart = this.track.getStorageValue(key, new DataWord(blake128(key2.toBytes()))).getData();
        byte[] addrSecondPart = this.track.getStorageValue(key, new DataWord(blake128(blake128(key2.toBytes())))).getData();
        return Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    private Address getAddressFromStorage(Address key, String key2){
        byte[] addrFirstPart = this.track.getStorageValue(key, new DataWord(blake128(key2.getBytes()))).getData();
        byte[] addrSecondPart = this.track.getStorageValue(key, new DataWord(blake128(blake128(key2.getBytes())))).getData();
        return Address.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    private BigInteger getBigIntegerFromStorage(Address key, String key2){
        IDataWord data = this.track.getStorageValue(key, new DataWord(blake128(key2.getBytes())));
        return new BigInteger(data.getData());
    }

    private BigInteger getBigIntegerFromStorage(Address key, Address key2){
        IDataWord data = this.track.getStorageValue(key, new DataWord(blake128(key2.toBytes())));
        return new BigInteger(data.getData());
    }

    private String getNameFromStorage(Address key, Address key2){
        byte[] domainNameFirstPart = this.track.getStorageValue(key, new DataWord(blake128(key2.toBytes()))).getData();
        byte[] domainNameSecondPart = this.track.getStorageValue(key, new DataWord(blake128(blake128(key2.toBytes())))).getData();
        String tempDomainName;
        try {
            tempDomainName = new String(trimLeadingZeros(combineTwoBytes(domainNameFirstPart, domainNameSecondPart)), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            tempDomainName = "";
        }
        return tempDomainName + ".aion";
    }

    private Date getDateFromStorage(Address key, Address key2){
        byte[] expireDateData = this.track.getStorageValue(key, new DataWord(blake128(key2.toBytes()))).getData();
        byte[] trimmedExpireDateData = trimLeadingZeros16(expireDateData);
        String expireDateStr;
        try {
            expireDateStr = new String(trimmedExpireDateData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            expireDateStr = "";
        }
        return new Date(Long.parseLong(expireDateStr));
    }

    private void addAddressToStorage(Address key, Address key2, Address value){
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(value.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(value.toBytes(), 16, addrSecondPart, 0, 16);

        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(addrFirstPart));
        this.track.addStorageRow(key, new DataWord(blake128(blake128(key2.toBytes()))), new DataWord(addrSecondPart));

    }

    private void addAddressToStorage(Address key, String key2, Address value){
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(value.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(value.toBytes(), 16, addrSecondPart, 0, 16);

        this.track.addStorageRow(key, new DataWord(blake128(key2.getBytes())), new DataWord(addrFirstPart));
        this.track.addStorageRow(key, new DataWord(blake128(blake128(key2.getBytes()))), new DataWord(addrSecondPart));
    }

    private void addDateToStorage(Address key, Address key2, Date value){
        long dateInLong =  value.getTime();
        String dateString = String.valueOf(dateInLong);
        byte[] date;

        try {
            date = dateString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            date = Longs.toByteArray(value.getTime());
            System.out.println("Could not resolve date properly");
            e.printStackTrace();
        }
        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(fillByteArray(date)));
    }

    private void addNameToStorage(Address key, Address key2, String name){
        byte[] nameFirstPart = name.substring(0,16).getBytes();
        byte[] nameSecondPart = name.substring(16,32).getBytes();
        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(nameFirstPart));
        this.track.addStorageRow(key, new DataWord(blake128(blake128(key2.toBytes()))), new DataWord(nameSecondPart));
    }

    private void addNameToStorage2(Address key, Address key2, String name){
        byte[] addZeros = addLeadingZeros(name.getBytes());
        byte[] value1 = new byte[16], value2 = new byte[16];
        System.arraycopy(addZeros, 0, value1, 0, 16);
        System.arraycopy(addZeros, 16, value2, 0, 16);
        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(value1));
        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(value2));
    }

    private void addBigIntegerToStorage(Address key, String key2, BigInteger value){
        this.track.addStorageRow(key, new DataWord(blake128(key2.getBytes())), new DataWord(value));
    }

    private void addBigIntegerToStorage(Address key, Address key2, BigInteger value){
        this.track.addStorageRow(key, new DataWord(blake128(key2.toBytes())), new DataWord(value));
    }

    // tasks ---------------------------------------------------------------------------------------------------------//
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
        long time;

        removeActiveDomain(Address input, long time) {
            domainAddress = input;
            this.time = time;
        }

        @Override
        public void run() {
            System.out.println("--------------------------PERFORMING TASK: removeActiveDomain--------------------------");
            removeActiveDomain(domainAddress, time);
        }
    }

    // data processing helpers ---------------------------------------------------------------------------------------//
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

    // Query functions and helpers -----------------------------------------------------------------------------------//
    private List<AuctionDomainsData> getAllAuctionDomains(){
        BigInteger numberOfDomainsTotal = getBigIntegerFromStorage(allAddresses, ALL_ADDR_COUNTER_KEY);

        List<AuctionDomainsData> auctions = new ArrayList<>();
        for (int i = 0; i < numberOfDomainsTotal.intValue(); i++){
            Address tempDomainAddr = getAddressFromStorage(allAddresses, ALL_ADDR_KEY + i);

            // if domain exists
            if(!this.track.getStorageValue(auctionDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).equals(DataWord.ZERO)){
                Date tempExpireDate = getDateFromStorage(auctionDomainsAddress, tempDomainAddr);
                String tempDomainName = getNameFromStorage(domainAddressNamePair, tempDomainAddr);
                BigInteger tempNumberOfBids = getBigIntegerFromStorage(tempDomainAddr, BID_KEY_COUNTER);
                AuctionDomainsData tempData = new AuctionDomainsData(tempDomainName, tempDomainAddr, tempExpireDate, tempNumberOfBids);
                auctions.add(tempData);
            }
        }
        return  auctions;
    }

    private HashMap<Address, BigInteger> getBidsForADomain(Address domainAddress){
        HashMap<Address, BigInteger> bids = new HashMap<>();
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        for (int i = 0; i < numberOfBids.intValue(); i++){
            Address bidderAddr = getAddressFromStorage(domainAddress, BID_KEY_ADDR + i);
            BigInteger bidAmount = getBigIntegerFromStorage(domainAddress, BID_KEY_VALUE + i);

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

    public void displayAllAuctionDomains(){
        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();
        int counter = 0;

        System.out.println("--------------------------AION NAME SERVICE QUERY: displayAllAuctionDomains (" + auctionDomainsList.size() + ")-----------------------------");
        for (AuctionDomainsData domain: auctionDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.completeDate);
            System.out.println("    Number of bids for this domain: " + domain.numberOfBids);
            counter++;
        }

        if (counter == 0)
            System.out.println("Currently there no domains in auction");
        System.out.println();
    }

    public void displayMyBids(ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());
        boolean hasNoBids = true;

        System.out.println("-----------------------------AION NAME SERVICE QUERY: displayMyBids----------------------------");

        if (!this.track.hasAccountState(callerAddress)){
            System.out.println("    The given account: " + callerAddress + " is not registered\n");
            return;
        }

        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();
        for (AuctionDomainsData domain: auctionDomainsList){
            Address tempDomainAddress = domain.domainAddress;
            HashMap<Address, BigInteger> bids = getBidsForADomain(tempDomainAddress);
            if(bids.containsKey(callerAddress)){
                hasNoBids = false;
                System.out.println("Domain name: " + domain.domainName );
                System.out.println("    Your bid value: " + bids.get(callerAddress).intValue());
            }
        }

        if(hasNoBids)
            System.out.println("    You currently have no active bids");

        System.out.println();
    }

    public void displayMyBidForDomain(String domainNameRaw, ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());
        System.out.println("--------------------------AION NAME SERVICE QUERY: displayMyBidForDomain--------------------------");

        if (!this.track.hasAccountState(callerAddress)){
            System.out.println("    The given account: " + callerAddress + " is not registered\n");
            return;
        }

        // process domain name
        byte[] domainNameInBytes = domainNameRaw.substring(0, domainNameRaw.length() - 5).getBytes();
        String domainName2 = new String(addLeadingZeros(domainNameInBytes));
        String domainName = domainName2.substring(5, 37);

        Address domainAddress = getAddressFromStorage(domainNameAddressPair, domainName);

        System.out.println("Domain name: " + domainNameRaw);

        // check if this domain is currently in auction
        if(this.track.getStorageValue(auctionDomainsAddress, new DataWord(blake128(domainAddress.toBytes()))).equals(DataWord.ZERO)){
            System.out.println("    This domain is not in auction\n");
            return;
        }

        HashMap<Address, BigInteger> bids = getBidsForADomain(domainAddress);
        if(bids.containsKey(callerAddress))
            System.out.println("    Your bid value: " + bids.get(this.address)); // does not use 'callerAddress' so people cant check other's bid

        else
            System.out.println("    You do not have a bid for this domain: ");
        System.out.println();
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

