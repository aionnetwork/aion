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

import static org.aion.crypto.HashUtil.blake128;

import com.google.common.primitives.Longs;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;
import org.apache.commons.collections4.map.LRUMap;

/**
 * The AionAuctionContract is used to register domain names. Accounts can start and bid in an
 * auction for each domain name. Auctions begin when a user puts in the first bid and the auction
 * will last for 72 hours. After the auction period, the results are calculated. The user with the
 * highest bid value will deposit the amount of the second highest bid, and become the owner of the
 * domain for a 1 year period, everyone else will get their bid value back.
 *
 * @author William
 */
public class AionAuctionContract extends StatefulPrecompiledContract {
    private static final AionAddress AION =
            AionAddress.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    private AionAddress activeDomainsAddress =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000600");
    private AionAddress activeDomainsAddressTime =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000601");
    private AionAddress activeDomainsAddressName =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000602");
    private AionAddress activeDomainsAddressValue =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000603");

    private AionAddress auctionDomainsAddress =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000700");
    private AionAddress auctionDomainsAddressName =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000702");

    private AionAddress allAddresses =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000800");
    private AionAddress domainNameAddressPair =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000801");
    private AionAddress domainAddressNamePair =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000802");

    private static final long COST = 20000L;
    private static final int SIG_LEN = 96;
    private static final int ADDR_LEN = 32;
    private static final BigInteger MINIMUM_AMOUNT = new BigInteger("100");

    private static final String BID_KEY_COUNTER = "bidKeyCounterKey";
    private static final String BID_KEY_ADDR = "bidderAddressKey";
    private static final String BID_KEY_VALUE = "bidValueKey";
    private static final String ALL_ADDR_KEY = "allAddressKey";
    private static final String ALL_ADDR_COUNTER_KEY = "allAddressKey";

    private BigInteger AUCTION_TIME = new BigInteger("259200000"); // 3 * 24 * 60 * 60 * 1000
    private BigInteger ACTIVE_TIME = new BigInteger("31536000000"); // 365 * 24 * 60 * 60 * 1000
    private int TEST_AUCTION_TIME = 2000;
    private int TEST_ACTIVE_TIME = 2000;

    private static int LRU_MAP_SIZE = 4; // this should be changed

    private final AionAddress callerAddress;
    private final IBlockchain blockchain;
    private static Timer timer = new Timer();
    private static LRUMap<String, AuctionDomainsData> auctionsMap = new LRUMap<>(LRU_MAP_SIZE);
    private static LRUMap<String, Map<AionAddress, BigInteger>> auctionBidsMap =
            new LRUMap<>(LRU_MAP_SIZE);
    private static final Set<String> privateAionDomainNames =
            new HashSet<>(Arrays.asList("network.aion", "foundation.aion", "enterprise.aion"));

    /**
     * Constructs a Aion Auction Contract object, ready to execute.
     *
     * @param track The repository
     * @param address The callerAddress of the calling account, use AION address for testing
     */
    public AionAuctionContract(
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track,
            AionAddress address,
            IBlockchain blockchain) {
        super(track);
        this.callerAddress = address;
        this.blockchain = blockchain;

        // if testing, set AUCTION_TIME and ACTIVE_TIME to test time periods
        if (callerAddress.equals(AION)) {
            AUCTION_TIME = BigInteger.valueOf(TEST_AUCTION_TIME);
            ACTIVE_TIME = BigInteger.valueOf(TEST_ACTIVE_TIME);
        }
    }

    /**
     * For bidding, input parameter of this method is a byte array whose bytes should be supplied in
     * the following expected format:
     *
     * <p>[1b domainNameLength] length of the byte array containing the domain name [mb domainName]
     * the domain name to bid, m > 0 [32b bidderAddress] callerAddress of the bidder, must be the
     * same as the callerAddress from the constructor [96b signature] signature of the bidder [1b
     * balanceLength] the length of the byte[] containing the bid value [nb balance] where n > 0
     *
     * <p>1 + m + 32 + 96 + 1 + n = 130 + m + n
     *
     * <p>For time extension request on active domain, input parameter of this method should be
     * supplied as:
     *
     * <p>[1b domainNameLength] length of the byte array containing the domain name [mb domainName]
     * the domain to time extend, m > 0 [32b callerAddress] should be the owner of the domain [96b
     * signature] signature of the caller [1b operator] set this to 0 for time extension
     *
     * <p>1 + m + 32 + 96 + 1 = 130 + m
     */
    @Override
    public FastVmTransactionResult execute(byte[] input, long nrg) {
        if (nrg < COST)
            return new FastVmTransactionResult(
                    FastVmResultCode.OUT_OF_NRG, 0, "insufficient energy".getBytes());

        // check length for both operations
        if (input.length < 131) {
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "incorrect input length".getBytes());
        }

        int domainNameLength = input[0];
        if (domainNameLength < 0)
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "incorrect input length".getBytes());

        // check if input is too short for extension function
        if (input.length < 130 + domainNameLength)
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "incorrect input length".getBytes());
        int balanceLength = input[129 + domainNameLength];

        if (balanceLength > 0) {
            if (input.length < 130 + domainNameLength + balanceLength) {
                return new FastVmTransactionResult(
                        FastVmResultCode.FAILURE, nrg - COST, "incorrect input length".getBytes());
            }
        }

        // sort and store the input data
        byte[] bidderAddressInByte = new byte[ADDR_LEN];
        byte[] sign = new byte[SIG_LEN];
        byte[] domainNameInBytes = new byte[domainNameLength];
        String domainName;
        AionAddress domainAddress;

        int offset = 0;
        offset++;
        System.arraycopy(input, offset, domainNameInBytes, 0, domainNameLength);
        offset = offset + domainNameLength;
        System.arraycopy(input, offset, bidderAddressInByte, 0, ADDR_LEN);
        offset = offset + ADDR_LEN;
        System.arraycopy(input, offset, sign, 0, SIG_LEN);
        offset = offset + SIG_LEN;
        offset++;

        String domainNameRaw = new String(domainNameInBytes);

        // check if the domain name already has active parent domain
        if (hasActiveParentDomain(domainNameRaw))
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE,
                    nrg - COST,
                    "the given domain name has a parent that is already active".getBytes());

        // check if the domain name is valid to register
        if (!isValidDomainName(domainNameRaw))
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "domain name is invalid".getBytes());

        // add zeros for storing
        byte[] domainNameInBytesWithZeros = addLeadingZeros(domainNameInBytes);
        domainName = new String(domainNameInBytesWithZeros);
        // remove the last part (.aion) before storing
        domainName = domainName.substring(0, 32);

        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        AionAddress bidderAddress = AionAddress.wrap(bidderAddressInByte);

        // user should have the signature signed with its callerAddress
        byte[] data = new byte[ADDR_LEN];
        System.arraycopy(bidderAddressInByte, 0, data, 0, ADDR_LEN);
        boolean b = ECKeyEd25519.verify(data, sig.getSignature(), sig.getPubkey(null));

        // verify public key matches owner
        if (!b) {
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "incorrect signature".getBytes());
        }

        if (!bidderAddress.equals(AionAddress.wrap(sig.getAddress()))) {
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "incorrect key".getBytes());
        }

        // if this domain name does not have an callerAddress
        if (this.track
                .getStorageValue(
                        domainNameAddressPair,
                        new DataWord(blake128(domainName.getBytes())).toWrapper())
                .equals(DoubleDataWord.ZERO)) {
            domainAddress = createAddressForDomain(domainName);
        } else { // extract the callerAddress corresponding to the domain name
            domainAddress = getAddressFromName(domainName);
        }

        // if request to extend time
        if (balanceLength < 1) {
            return extensionRequest(domainAddress, nrg);
        }

        // get the bid value
        byte[] balance = new byte[balanceLength];
        System.arraycopy(input, offset, balance, 0, balanceLength);
        BigInteger bidValue = new BigInteger(balance);

        // check if bidValue is valid (greater than 0)
        if (bidValue.compareTo(new BigInteger("0")) < 0)
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, nrg - COST, "negative bid value".getBytes());

        // check bidder addr and its balance
        if (this.track.hasAccountState(bidderAddress)) {
            if (this.track.getAccountState(bidderAddress).getBalance().compareTo(bidValue) < 0) {
                return new FastVmTransactionResult(
                        FastVmResultCode.FAILURE, nrg - COST, "insufficient balance".getBytes());
            }
        } else
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE,
                    nrg - COST,
                    "bidder account does not exist".getBytes());

        // if this domain is already active
        if (isActiveDomain(domainAddress)) {
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE,
                    nrg - COST,
                    "requested domain is already active".getBytes());
        }

        // if this domain is already in auction state
        else if (isAuctionDomain(domainAddress)) {
            processBid(domainNameRaw, domainAddress, bidderAddress, bidValue);
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS, nrg - COST, domainAddress.toBytes());
        }

        // start the auction for the given domain
        else {
            storeNewAddress(domainAddress);
            addToAuctionDomain(domainAddress, domainName, domainNameRaw);
            processBid(domainNameRaw, domainAddress, bidderAddress, bidValue);
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS, nrg - COST, domainAddress.toBytes());
        }
    }

    /**
     * Store the given domain callerAddress in the collection of domains in auction process. Set a
     * new task, scheduling the auction to run for 3 days and then completed.
     *
     * @param domainAddress callerAddress of the domain bid for
     */
    private void addToAuctionDomain(
            AionAddress domainAddress, String domainName, String domainNameRaw) {
        Date currentDate = new Date();
        Date finishDate =
                new Date(currentDate.getTime() + AUCTION_TIME.intValue()); // 3 days later, 3s

        // long time = blockchain.getBestBlock().getTimestamp();
        // Date finishDate = new Date(time + AUCTION_TIME);

        addDateToStorage(auctionDomainsAddress, domainAddress, finishDate);
        addNameToStorage(auctionDomainsAddressName, domainAddress, domainName);

        // store in auction LRU map
        AuctionDomainsData tempData =
                new AuctionDomainsData(domainNameRaw, domainAddress, finishDate, BigInteger.ZERO);
        auctionsMap.put(domainNameRaw, tempData);

        TimerTask auctionTask = new finishAuction(domainAddress);
        timer.schedule(auctionTask, finishDate);
    }

    // record to data base, change stuff, so when its time for task to execute, it will first check
    // database
    // to see if it has been extended, if it has, schedule new task.
    private FastVmTransactionResult extensionRequest(AionAddress domainAddress, long nrg) {
        Date expireDateFromStorage = getDateFromStorage(activeDomainsAddressTime, domainAddress);
        // Date currentDate = new Date(blockchain.getBestBlock().getTimestamp());
        Date currentDate = new Date();

        long difference = expireDateFromStorage.getTime() - currentDate.getTime();

        // check if domain is currently active, but have not been extended
        if (expireDateFromStorage.getTime() < currentDate.getTime()
                || difference > ACTIVE_TIME.intValue()) {
            return new FastVmTransactionResult(
                    FastVmResultCode.FAILURE, COST - nrg, "already been extended".getBytes());
        }

        // add the new expire date
        Date finishDate =
                new Date(
                        expireDateFromStorage.getTime()
                                + ACTIVE_TIME.intValue()); // extend for 1 period
        addDateToStorage(activeDomainsAddressTime, domainAddress, finishDate);
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, COST - nrg);
    }

    /**
     * Store the given domain callerAddress in the collection of active domains. Set a new task,
     * scheduling the domain to be active for a period of 1 year.
     *
     * @param domainAddress callerAddress of the domain to be added
     * @param ownerAddress new owner of the given domain
     */
    private void addToActiveDomains(
            AionAddress domainAddress,
            AionAddress ownerAddress,
            String domainName,
            BigInteger value) {
        Date currentDate = new Date();
        Date finishDate =
                new Date(currentDate.getTime() + ACTIVE_TIME.intValue()); // 1 year later, 5s

        // long time = blockchain.getBestBlock().getTimestamp();
        // Date finishDate = new Date(time + ACTIVE_TIME);

        addBigIntegerToStorage(activeDomainsAddressValue, domainAddress, value);
        addDateToStorage(activeDomainsAddressTime, domainAddress, finishDate);
        addAddressToStorage(activeDomainsAddress, domainAddress, ownerAddress);
        addNameToStorage2(activeDomainsAddressName, domainAddress, domainName);

        TimerTask removeActiveDomainTask =
                new removeActiveDomain(domainAddress, finishDate.getTime());
        timer.schedule(removeActiveDomainTask, finishDate);
    }

    /**
     * Process the given bid. Increment the number of bids counter of the domain and call function
     * to store.
     *
     * @param domainNameRaw domain name with .aion
     * @param domainAddress domain to bid for
     * @param bidderAddress callerAddress of the bidder
     * @param value the bid value
     */
    private void processBid(
            String domainNameRaw,
            AionAddress domainAddress,
            AionAddress bidderAddress,
            BigInteger value) {
        this.track.getAccountState(bidderAddress).subFromBalance(value);
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        addBidToRepo(domainAddress, numberOfBids.intValue(), bidderAddress, value);
        numberOfBids = numberOfBids.add(BigInteger.valueOf(1));
        addBigIntegerToStorage(domainAddress, BID_KEY_COUNTER, numberOfBids);

        // if domain is in auction LRUMap, update bid counter by 1
        if (auctionsMap.containsKey(domainNameRaw)) {
            AuctionDomainsData oldData = auctionsMap.get(domainNameRaw);
            AuctionDomainsData newData =
                    new AuctionDomainsData(
                            oldData.domainName,
                            oldData.domainAddress,
                            oldData.completeDate,
                            oldData.numberOfBids.add(BigInteger.ONE));
            auctionsMap.put(domainNameRaw, newData);
        }

        // if auction domain is not in auction LRUMap(overwritten by other auctions) get its data
        // from repo.
        // Add info to auction LRUMap and increment the number of bids counter by 1
        else {
            Date tempExpireDate = getDateFromStorage(auctionDomainsAddress, domainAddress);
            BigInteger tempNumberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);
            AuctionDomainsData tempData =
                    new AuctionDomainsData(
                            domainNameRaw,
                            domainAddress,
                            tempExpireDate,
                            tempNumberOfBids.add(BigInteger.ONE));
            auctionsMap.put(domainNameRaw, tempData);
        }

        // if domain is in bids LRUMap, add current bid to data
        if (auctionBidsMap.containsKey(domainNameRaw)) {
            auctionBidsMap.get(domainNameRaw).put(domainAddress, value);
        }

        // if domain is not in bids LRUMap
        // create the map and put into bids LRUMap
        else {
            Map<AionAddress, BigInteger> bids = new HashMap<>();
            bids.put(bidderAddress, value);
            auctionBidsMap.put(domainNameRaw, bids);
        }
    }

    /**
     * Process the auction at the given domain callerAddress and find: - bidder with the highest big
     * (Address) - second highest bid value (BigInteger) Record information in repository and clean
     * up auction values in repo.
     *
     * @param domainAddress The domain callerAddress of the auction to be processed
     */
    private void processAuction(AionAddress domainAddress) {
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        // if there are no bids, cancel the auction, no one wins, this should never happen
        // since a first bid is needed to begin an auction
        if (numberOfBids.intValue() < 1) return;

        String domainName;
        AionAddress winnerAddress = null;
        BigInteger highestBid = MINIMUM_AMOUNT;
        BigInteger secondHighestBid = new BigInteger("0");

        AionAddress tempAddress;
        BigInteger tempAmount;

        for (int i = 0; i < numberOfBids.intValue(); i++) {
            tempAddress = getAddressFromStorage(domainAddress, BID_KEY_ADDR + i);
            tempAmount = getBigIntegerFromStorage(domainAddress, BID_KEY_VALUE + i);

            // if current winner and temp are same person, only update the highest bid
            if (winnerAddress != null
                    && Arrays.equals(tempAddress.toBytes(), winnerAddress.toBytes())) {
                // return the smaller amount to account
                if (tempAmount.compareTo(highestBid) > 0) {
                    this.track.addBalance(tempAddress, highestBid);
                    highestBid = tempAmount;
                } else {
                    this.track.addBalance(tempAddress, tempAmount);
                }
            }

            // if temp callerAddress is different from winner callerAddress
            else {
                if (tempAmount.compareTo(highestBid) > 0) {
                    // return previous winner and amount to its bidder
                    this.track.addBalance(winnerAddress, highestBid);
                    // set winner variables
                    secondHighestBid = highestBid;
                    highestBid = tempAmount;
                    winnerAddress = tempAddress;
                } else if (tempAmount.compareTo(secondHighestBid) > 0) {
                    this.track.addBalance(tempAddress, tempAmount);
                    secondHighestBid = tempAmount;
                } else {
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
        this.track.addStorageRow(
                auctionDomainsAddress,
                new DataWord(blake128(domainAddress.toBytes())).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        addToActiveDomains(domainAddress, winnerAddress, domainName, secondHighestBid);
        printWinner(domainAddress, winnerAddress, secondHighestBid, domainName);
    }

    /**
     * insert (16 - length of input) 0s to the beginning of the byte, used to store into Dataword.
     *
     * @param inputBytes the byte[] to be filled to 16 length
     * @return the filled byte array
     */
    private byte[] fillByteArray(byte[] inputBytes) {
        byte[] ret = new byte[16];
        int length = inputBytes.length;
        System.arraycopy(inputBytes, 0, ret, (16 - length), length);
        return ret;
    }

    /**
     * Checks if the domain given is an active domain.
     *
     * @param domainAddress a domain callerAddress
     * @return the trimmed byte array
     */
    private boolean isActiveDomain(AionAddress domainAddress) {
        DataWord key = new DataWord(blake128(domainAddress.toBytes()));
        return !(this.track
                .getStorageValue(activeDomainsAddress, key.toWrapper())
                .equals(DoubleDataWord.ZERO.toWrapper()));
    }

    /**
     * Checks if the given domain is in auction process
     *
     * @param domainAddress a domain callerAddress
     */
    private boolean isAuctionDomain(AionAddress domainAddress) {
        DataWord key = new DataWord(blake128(domainAddress.toBytes()));
        ByteArrayWrapper ret = this.track.getStorageValue(auctionDomainsAddress, key.toWrapper());
        return !ret.equals(DoubleDataWord.ZERO.toWrapper());
    }

    /**
     * Store the given bid in the repository under the given domain callerAddress. Stores bidder
     * callerAddress and the bid value with corresponding hash key.
     *
     * @param domainAddress domain of the bid
     * @param offset index for storage key
     * @param bidderAddress callerAddress of the bidder
     * @param value the bid value
     */
    private void addBidToRepo(
            AionAddress domainAddress, int offset, AionAddress bidderAddress, BigInteger value) {
        addBigIntegerToStorage(domainAddress, BID_KEY_VALUE + offset, value);
        addAddressToStorage(domainAddress, BID_KEY_ADDR + offset, bidderAddress);
    }

    /**
     * Prints out information for the auction of the given domain.
     *
     * @param domainAddress callerAddress of the domain
     * @param winnerAddress callerAddress of the auction winner (new owner of domain)
     * @param value the value (second highest) to deposit
     */
    private void printWinner(
            AionAddress domainAddress,
            AionAddress winnerAddress,
            BigInteger value,
            String domainName) {
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
     * @param domainAddress callerAddress of the domain
     */
    private void printRemoveActiveDomain(AionAddress domainAddress) {
        System.out.println("Removing active domain at: " + domainAddress);
        Date terminateDate = new Date();
        System.out.println("    Terminate time at: " + terminateDate.toString());
        System.out.println();
    }

    /**
     * Removes the given domain from the collection of active domains in repo.
     *
     * @param domainAddress callerAddress of the domain
     */
    private void removeActiveDomain(AionAddress domainAddress, long expireTime) {
        // retrieve expire time from storage
        Date expireDateFromStorage = getDateFromStorage(activeDomainsAddressTime, domainAddress);

        // if domain active time has been extended, schedule new task and return
        if (expireDateFromStorage.getTime() > expireTime) {
            System.out.println(
                    "    domain active time has been extended, schedule new task for : "
                            + expireDateFromStorage
                            + "\n");
            TimerTask removeActiveDomainTask =
                    new removeActiveDomain(domainAddress, expireDateFromStorage.getTime());
            timer.schedule(removeActiveDomainTask, expireDateFromStorage);
            return;
        }

        // return deposit
        AionAddress ownerAddress = getAddressFromStorage(activeDomainsAddress, domainAddress);
        BigInteger tempValue = getBigIntegerFromStorage(activeDomainsAddressValue, domainAddress);
        this.track.addBalance(ownerAddress, tempValue);

        printRemoveActiveDomain(domainAddress);
        // erase
        this.track.addStorageRow(
                activeDomainsAddress,
                new DataWord(blake128(domainAddress.toBytes())).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        this.track.addStorageRow(
                activeDomainsAddress,
                new DataWord(blake128(blake128(domainAddress.toBytes()))).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        this.track.addStorageRow(
                activeDomainsAddressName,
                new DataWord(blake128(domainAddress.toBytes())).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        this.track.addStorageRow(
                activeDomainsAddressName,
                new DataWord(blake128(blake128(domainAddress.toBytes()))).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        this.track.addStorageRow(
                activeDomainsAddressValue,
                new DataWord(blake128(domainAddress.toBytes())).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
        this.track.addStorageRow(
                activeDomainsAddressTime,
                new DataWord(blake128(domainAddress.toBytes())).toWrapper(),
                DoubleDataWord.ZERO.toWrapper());
    }

    /**
     * Checks if the domain name follows the rules: - between 8-37 characters in length (including
     * the .aion at the end) - follows the ascii base code - end with .aion - are at max 4 levels
     * including .aion head ("a.a.a.aion" is valid while a.a.a.a.aion is invalid"
     *
     * @param domainNameRaw name of the domain
     */
    private boolean isValidDomainName(String domainNameRaw) {
        if (domainNameRaw.length() < 8 || domainNameRaw.length() > 37) return false;
        if (!domainNameRaw.matches("[a-zA-Z0-9.]*")) return false;
        String[] domainPartitioned = domainNameRaw.split("\\.");
        if (!domainPartitioned[domainPartitioned.length - 1].equals("aion")) return false;
        if (privateAionDomainNames.contains(domainNameRaw)) return false;
        return domainPartitioned.length <= 4;
    }

    /**
     * Checks whether the given domain has a parent domain that is already active, if so, the given
     * (sub) domain is not allowed to be auctioned.
     *
     * @param domainName
     */
    private boolean hasActiveParentDomain(String domainName) {
        String[] domainPartitioned = domainName.split("\\.");
        int numberOfSplits = domainPartitioned.length;
        if (numberOfSplits < 3) return false;

        // dont use the first and last partitioned string
        for (int i = 1; i < numberOfSplits - 1; i++) {
            String tempParentName = "";

            // generate each possible parent domain
            for (int j = i; j < numberOfSplits - 1; j++) {
                tempParentName = tempParentName + domainPartitioned[i];
            }

            // check if domain exists, and if it is active
            AionAddress parentAddr = null;
            try {
                parentAddr =
                        getAddressFromName(
                                new String((addLeadingZeros(tempParentName.getBytes())), "UTF-8")
                                        .substring(5, 37));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            if (!parentAddr.isZeroAddress()) {
                if (isActiveDomain(parentAddr)) return true;
            }
        }
        return false;
    }

    // storage processing
    // --------------------------------------------------------------------------------------------//

    /**
     * Create an aion callerAddress for the given domain name and store it
     *
     * @param domainName name of the domain
     * @return callerAddress generated for the domain
     */
    private AionAddress createAddressForDomain(String domainName) {
        ECKey domainAddr = ECKeyFac.inst().create();
        AionAddress domainAddress = AionAddress.wrap(domainAddr.getAddress());

        // store callerAddress -> name pair & name -> callerAddress pair
        addNameToStorage(domainAddressNamePair, domainAddress, domainName);
        addAddressToStorage(domainNameAddressPair, domainName, domainAddress);
        return domainAddress;
    }
    /**
     * Get the corresponding domainAddress for the given domain name
     *
     * @param domainName name of domain
     * @return callerAddress of domain
     */
    private AionAddress getAddressFromName(String domainName) {
        byte[] addrFirstPart =
                this.track
                        .getStorageValue(
                                domainNameAddressPair,
                                new DataWord(blake128(domainName.getBytes())).toWrapper())
                        .getData();
        byte[] addrSecondPart =
                this.track
                        .getStorageValue(
                                domainNameAddressPair,
                                new DataWord(blake128(blake128(domainName.getBytes()))).toWrapper())
                        .getData();
        return AionAddress.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    /**
     * Stores the domain callerAddress into a collection of all domain addresses also increment the
     * count by 1
     *
     * @param domainAddress callerAddress of domain
     */
    private void storeNewAddress(AionAddress domainAddress) {
        BigInteger counter = getBigIntegerFromStorage(allAddresses, ALL_ADDR_COUNTER_KEY);
        this.track.addStorageRow(
                allAddresses,
                new DataWord(blake128(ALL_ADDR_COUNTER_KEY.getBytes())).toWrapper(),
                new DataWord(counter.add(BigInteger.ONE)).toWrapper());
        addAddressToStorage(allAddresses, ALL_ADDR_KEY + counter, domainAddress);
    }

    private AionAddress getAddressFromStorage(AionAddress key, AionAddress key2) {
        byte[] addrFirstPart =
                this.track
                        .getStorageValue(key, new DataWord(blake128(key2.toBytes())).toWrapper())
                        .getData();
        byte[] addrSecondPart =
                this.track
                        .getStorageValue(
                                key, new DataWord(blake128(blake128(key2.toBytes()))).toWrapper())
                        .getData();
        return AionAddress.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    private AionAddress getAddressFromStorage(AionAddress key, String key2) {
        byte[] addrFirstPart =
                this.track
                        .getStorageValue(key, new DataWord(blake128(key2.getBytes())).toWrapper())
                        .getData();
        byte[] addrSecondPart =
                this.track
                        .getStorageValue(
                                key, new DataWord(blake128(blake128(key2.getBytes()))).toWrapper())
                        .getData();
        return AionAddress.wrap(combineTwoBytes(addrFirstPart, addrSecondPart));
    }

    private BigInteger getBigIntegerFromStorage(AionAddress key, String key2) {
        ByteArrayWrapper data =
                this.track.getStorageValue(
                        key, new DataWord(blake128(key2.getBytes())).toWrapper());
        return new BigInteger(data.getData());
    }

    private BigInteger getBigIntegerFromStorage(AionAddress key, AionAddress key2) {
        ByteArrayWrapper data =
                this.track.getStorageValue(key, new DataWord(blake128(key2.toBytes())).toWrapper());
        return new BigInteger(data.getData());
    }

    private String getNameFromStorage(AionAddress key, AionAddress key2) {
        byte[] domainNameFirstPart =
                this.track
                        .getStorageValue(key, new DataWord(blake128(key2.toBytes())).toWrapper())
                        .getData();
        byte[] domainNameSecondPart =
                this.track
                        .getStorageValue(
                                key, new DataWord(blake128(blake128(key2.toBytes()))).toWrapper())
                        .getData();
        String tempDomainName;
        try {
            tempDomainName =
                    new String(
                            trimLeadingZeros(
                                    combineTwoBytes(domainNameFirstPart, domainNameSecondPart)),
                            "UTF-8");
        } catch (UnsupportedEncodingException e) {
            tempDomainName = "";
        }
        return tempDomainName + ".aion";
    }

    private Date getDateFromStorage(AionAddress key, AionAddress key2) {
        byte[] expireDateData =
                this.track
                        .getStorageValue(key, new DataWord(blake128(key2.toBytes())).toWrapper())
                        .getData();
        byte[] trimmedExpireDateData = trimLeadingZeros16(expireDateData);
        String expireDateStr;
        try {
            expireDateStr = new String(trimmedExpireDateData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            expireDateStr = "";
        }
        return new Date(Long.parseLong(expireDateStr));
    }

    private void addAddressToStorage(AionAddress key, AionAddress key2, AionAddress value) {
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(value.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(value.toBytes(), 16, addrSecondPart, 0, 16);

        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(addrFirstPart).toWrapper());
        this.track.addStorageRow(
                key,
                new DataWord(blake128(blake128(key2.toBytes()))).toWrapper(),
                new DataWord(addrSecondPart).toWrapper());
    }

    private void addAddressToStorage(AionAddress key, String key2, AionAddress value) {
        byte[] addrFirstPart = new byte[16];
        byte[] addrSecondPart = new byte[16];
        System.arraycopy(value.toBytes(), 0, addrFirstPart, 0, 16);
        System.arraycopy(value.toBytes(), 16, addrSecondPart, 0, 16);

        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.getBytes())).toWrapper(),
                new DataWord(addrFirstPart).toWrapper());
        this.track.addStorageRow(
                key,
                new DataWord(blake128(blake128(key2.getBytes()))).toWrapper(),
                new DataWord(addrSecondPart).toWrapper());
    }

    private void addDateToStorage(AionAddress key, AionAddress key2, Date value) {
        long dateInLong = value.getTime();
        String dateString = String.valueOf(dateInLong);
        byte[] date;

        try {
            date = dateString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            date = Longs.toByteArray(value.getTime());
            System.out.println("Could not resolve date properly");
            e.printStackTrace();
        }
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(fillByteArray(date)).toWrapper());
    }

    private void addNameToStorage(AionAddress key, AionAddress key2, String name) {
        byte[] nameFirstPart = name.substring(0, 16).getBytes();
        byte[] nameSecondPart = name.substring(16, 32).getBytes();
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(nameFirstPart).toWrapper());
        this.track.addStorageRow(
                key,
                new DataWord(blake128(blake128(key2.toBytes()))).toWrapper(),
                new DataWord(nameSecondPart).toWrapper());
    }

    private void addNameToStorage2(AionAddress key, AionAddress key2, String name) {
        byte[] addZeros = addLeadingZeros(name.getBytes());
        byte[] value1 = new byte[16], value2 = new byte[16];
        System.arraycopy(addZeros, 0, value1, 0, 16);
        System.arraycopy(addZeros, 16, value2, 0, 16);
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(value1).toWrapper());
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(value2).toWrapper());
    }

    private void addBigIntegerToStorage(AionAddress key, String key2, BigInteger value) {
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.getBytes())).toWrapper(),
                new DataWord(value).toWrapper());
    }

    private void addBigIntegerToStorage(AionAddress key, AionAddress key2, BigInteger value) {
        this.track.addStorageRow(
                key,
                new DataWord(blake128(key2.toBytes())).toWrapper(),
                new DataWord(value).toWrapper());
    }

    // tasks
    // ---------------------------------------------------------------------------------------------------------//
    class finishAuction extends TimerTask {
        AionAddress domainAddress;

        finishAuction(AionAddress input) {
            domainAddress = input;
        }

        @Override
        public void run() {
            System.out.println(
                    "--------------------------PERFORMING TASK: finishAuction--------------------------");
            processAuction(domainAddress);
        }
    }

    class removeActiveDomain extends TimerTask {
        AionAddress domainAddress;
        long time;

        removeActiveDomain(AionAddress input, long time) {
            domainAddress = input;
            this.time = time;
        }

        @Override
        public void run() {
            System.out.println(
                    "--------------------------PERFORMING TASK: removeActiveDomain--------------------------");
            removeActiveDomain(domainAddress, time);
        }
    }

    // data processing helpers
    // ---------------------------------------------------------------------------------------//
    /**
     * Combines two length 16 byte[] (usually retrieved from repo as a DataWord(length 16) into a
     * length 32 byte[].
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

    private byte[] trimLeadingZeros(byte[] b) {
        if (b == null) return null;

        int counter = 0;
        for (int i = 0; i < 32; i++) {
            if (b[i] != 0) break;
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
            if (b[i] != 0) break;
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

    // Query functions and helpers
    // -----------------------------------------------------------------------------------//
    private List<AuctionDomainsData> getAllAuctionDomains() {
        BigInteger numberOfDomainsTotal =
                getBigIntegerFromStorage(allAddresses, ALL_ADDR_COUNTER_KEY);

        List<AuctionDomainsData> auctions = new ArrayList<>();
        for (int i = 0; i < numberOfDomainsTotal.intValue(); i++) {
            AionAddress tempDomainAddr = getAddressFromStorage(allAddresses, ALL_ADDR_KEY + i);

            // if domain exists
            if (!this.track
                    .getStorageValue(
                            auctionDomainsAddress,
                            new DataWord(blake128(tempDomainAddr.toBytes())).toWrapper())
                    .equals(DoubleDataWord.ZERO)) {
                Date tempExpireDate = getDateFromStorage(auctionDomainsAddress, tempDomainAddr);
                String tempDomainName = getNameFromStorage(domainAddressNamePair, tempDomainAddr);
                BigInteger tempNumberOfBids =
                        getBigIntegerFromStorage(tempDomainAddr, BID_KEY_COUNTER);
                AuctionDomainsData tempData =
                        new AuctionDomainsData(
                                tempDomainName, tempDomainAddr, tempExpireDate, tempNumberOfBids);
                auctions.add(tempData);
            }
        }
        return auctions;
    }

    private HashMap<AionAddress, BigInteger> getBidsForADomain(AionAddress domainAddress) {
        HashMap<AionAddress, BigInteger> bids = new HashMap<>();
        BigInteger numberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);

        for (int i = 0; i < numberOfBids.intValue(); i++) {
            AionAddress bidderAddr = getAddressFromStorage(domainAddress, BID_KEY_ADDR + i);
            BigInteger bidAmount = getBigIntegerFromStorage(domainAddress, BID_KEY_VALUE + i);

            // check if there is multiple bids from same callerAddress
            if (bids.containsKey(bidderAddr)) {
                if (bidAmount.compareTo(bids.get(bidderAddr)) > 0) bids.put(bidderAddr, bidAmount);
            } else {
                bids.put(bidderAddr, bidAmount);
            }
        }
        return bids;
    }

    public void displayAllAuctionDomains() {
        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();
        int counter = 0;

        System.out.println(
                "--------------------------AION NAME SERVICE QUERY: displayAllAuctionDomains ("
                        + auctionDomainsList.size()
                        + ")-----------------------------");
        for (AuctionDomainsData domain : auctionDomainsList) {
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Domain callerAddress: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.completeDate);
            System.out.println("    Number of bids for this domain: " + domain.numberOfBids);
            counter++;
        }

        if (counter == 0) System.out.println("Currently there no domains in auction");
        System.out.println();
    }

    public void displayMyBidsLRU(ECKey key) {
        AionAddress callerAddress = AionAddress.wrap(key.getAddress());
        boolean hasNoBids = true;

        System.out.println(
                "-----------------------------AION NAME SERVICE QUERY: displayMyBidsLRU----------------------------");

        if (!this.track.hasAccountState(callerAddress)) {
            System.out.println("    The given account: " + callerAddress + " is not registered\n");
            return;
        }

        List<AuctionDomainsData> auctionDomainsList = getAllAuctionDomains();
        for (AuctionDomainsData domain : auctionDomainsList) {
            AionAddress tempDomainAddress = domain.domainAddress;
            String tempDomainName = domain.domainName;

            auctionBidsMap.put(tempDomainName, getBidsForADomain(tempDomainAddress));
            // if bidder is there
            if (auctionBidsMap.get(tempDomainName).containsKey(callerAddress)) {
                printBid(tempDomainName, callerAddress);
                hasNoBids = false;
            }
        }

        if (hasNoBids) System.out.println("    You currently have no active bids");

        System.out.println();
    }

    public void displayMyBidForDomainLRU(String domainNameRaw, ECKey key) {
        AionAddress callerAddress = AionAddress.wrap(key.getAddress());
        System.out.println(
                "--------------------------AION NAME SERVICE QUERY: displayMyBidForDomainLRU--------------------------");

        if (!this.track.hasAccountState(callerAddress)) {
            System.out.println("    The given account: " + callerAddress + " is not registered\n");
            return;
        }

        // process domain name
        byte[] domainNameInBytes =
                domainNameRaw.substring(0, domainNameRaw.length() - 5).getBytes();
        String domainName2 = new String(addLeadingZeros(domainNameInBytes));
        String domainName = domainName2.substring(5, 37);
        AionAddress domainAddress = getAddressFromStorage(domainNameAddressPair, domainName);

        // if the domain is not in auction, print error and return
        if (this.track
                .getStorageValue(
                        auctionDomainsAddress,
                        new DataWord(blake128(domainAddress.toBytes())).toWrapper())
                .equals(DoubleDataWord.ZERO)) {
            System.out.println("    This domain is not in auction\n");
            return;
        }

        auctionBidsMap.put(domainNameRaw, getBidsForADomain(domainAddress));

        if (auctionBidsMap.containsKey(domainNameRaw)) {
            if (auctionBidsMap.get(domainNameRaw).containsKey(callerAddress)) {
                printBid(domainNameRaw, callerAddress);
            } else {
                System.out.println("You have no bids for: " + domainNameRaw);
            }
        }

        System.out.println();
    }

    public void displayAuctionDomainLRU(String domainNameRaw) {
        System.out.println(
                "--------------------------AION NAME SERVICE QUERY: displayAuctionDomainLRU--------------------------");
        // process domain name
        byte[] domainNameInBytes =
                domainNameRaw.substring(0, domainNameRaw.length() - 5).getBytes();
        String domainName2 = new String(addLeadingZeros(domainNameInBytes));
        String domainName = domainName2.substring(5, 37);

        AionAddress domainAddress = getAddressFromStorage(domainNameAddressPair, domainName);

        // if the domain is not in auction, return
        if (this.track
                .getStorageValue(
                        auctionDomainsAddress,
                        new DataWord(blake128(domainAddress.toBytes())).toWrapper())
                .equals(DoubleDataWord.ZERO)) {
            System.out.println("The given domain \'" + domainNameRaw + "\' is not in auction\n");
            return;
        }

        // if domain is in LRUMap, print content
        if (auctionsMap.containsKey(domainNameRaw)) {
            printAuctionDomain(domainNameRaw);
            System.out.println();
        }

        // if domain is not in LRUMap get its data from repo. Add info to LRUMap and print content
        else {
            Date tempExpireDate = getDateFromStorage(auctionDomainsAddress, domainAddress);
            String tempDomainName = getNameFromStorage(domainAddressNamePair, domainAddress);
            BigInteger tempNumberOfBids = getBigIntegerFromStorage(domainAddress, BID_KEY_COUNTER);
            AuctionDomainsData tempData =
                    new AuctionDomainsData(
                            tempDomainName, domainAddress, tempExpireDate, tempNumberOfBids);
            auctionsMap.put(tempDomainName, tempData);
            printAuctionDomain(tempDomainName);
            System.out.println();
        }
    }

    private void printAuctionDomain(String domainName) {
        System.out.println("Domain name: " + auctionsMap.get(domainName).domainName);
        System.out.println(
                "    Domain callerAddress: " + auctionsMap.get(domainName).domainAddress);
        System.out.println(
                "    Auction complete date: " + auctionsMap.get(domainName).completeDate);
        System.out.println(
                "    Number of bids for this domain: " + auctionsMap.get(domainName).numberOfBids);
    }

    private void printBid(String domainName, AionAddress bidderAddress) {
        System.out.println("Domain name: " + domainName);
        System.out.println("    Bid value: " + auctionBidsMap.get(domainName).get(bidderAddress));
    }

    class AuctionDomainsData {
        String domainName;
        AionAddress domainAddress;
        Date completeDate;
        BigInteger numberOfBids;

        AuctionDomainsData(
                String domainName,
                AionAddress domainAddress,
                Date completeDate,
                BigInteger numberOfBids) {
            this.domainName = domainName;
            this.domainAddress = domainAddress;
            this.completeDate = completeDate;
            this.numberOfBids = numberOfBids;
        }
    }
}
