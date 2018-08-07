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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.*;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.apache.commons.collections4.map.LRUMap;

/**
 * Aion Name Service Contract
 *
 * <p>Architecture: Registry consists of a single central contract that maintains a list of all
 * domains and sub-domains, and stores: owners of domain (external acc/user or smart contract)
 * resolver of domain time-to-live for all records under the domain Resolvers are responsible for
 * the actual process of translating names into address
 *
 * @author William
 */
public class AionNameServiceContract extends StatefulPrecompiledContract {

    // set to a default cost for now, this will need to be adjusted
    private static final Map<String, Address> domains = new HashMap<>();
    private static final long SET_COST = 1000;
    private static final long TRANSFER_COST = 2000;
    private static final String RESOLVER_HASH = "ResolverHash";
    private static final String OWNER_HASH = "OwnerHash";
    private static final String TTL_HASH = "TTLHash";
    private static final String ALL_ADDR_KEY = "allAddressKey";
    private static final String ALL_ADDR_COUNTER_KEY = "allAddressKey";

    private Address activeDomainsAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000600");
    private Address activeDomainsAddressTime = Address.wrap("0000000000000000000000000000000000000000000000000000000000000601");
    private Address activeDomainsAddressValue = Address.wrap("0000000000000000000000000000000000000000000000000000000000000603");
    private Address allAddresses = Address.wrap("0000000000000000000000000000000000000000000000000000000000000800");
    private Address domainAddressNamePair = Address.wrap("0000000000000000000000000000000000000000000000000000000000000802");
    private Address registeredDomainAddressName =  Address.wrap("0000000000000000000000000000000000000000000000000000000000000803");
    private Address registeredDomainNameAddress =  Address.wrap("0000000000000000000000000000000000000000000000000000000000000804");

    private Address address;
    private Address ownerAddress;
    private Address ownerAddressKey;
    private Address resolverAddressKey;
    private Address TTLKey;
    private String domainName;

    private static LRUMap<String, AionAuctionContract.AuctionDomainsData> activeDomains = new LRUMap(4);

    /** Construct a new ANS Contract */
    public AionNameServiceContract(IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address address, Address ownerAddress) { // byte
        super(track);
        this.address = address;
        setUpKeys();
        if (!isValidDomainAddress(address)) {
            throw new IllegalArgumentException(
                    "Invalid domain address, check for aion prefix: 0xa0(66char) or a0(64char)\n");
        }
        if (!isValidOwnerAddress(ownerAddress)) {
            throw new IllegalArgumentException(
                    "The owner address does not exist in the given repository\n");
        }
        if (!(getOwnerAddress().equals(ownerAddress))
                && !(getOwnerAddress()
                .equals(
                        Address.wrap(
                                "0000000000000000000000000000000000000000000000000000000000000000")))) {
            throw new IllegalArgumentException(
                    "The owner address of this domain from repository is different than the given"
                            + "owner address from the input\n");
        }

        if (!isAvailableDomain(address, ownerAddress)) {
            System.out.print(" This domain is not available for you\n");
            throw new IllegalArgumentException(
                    address.toString() + " This domain is not available for you\n");

        }
        this.ownerAddress = ownerAddress;
        String domainName = getDomainNameFromAddress(address);
        domains.put(domainName, this.address);
        // add to list
        addToRegistered(this.address, domainName);
    }

    /**
     * Execute the ANS contract
     *
     * input[] is defined as:
     *      [1b chainID] - chainId is intended to be our current chainId, in the case of the first AION network this
     *                     should be set to 1
     *      [1b operation] - <1: set resolver, 2: set time to live, 3: transfer domain ownership, 4: transfer subdomain
     *                       ownership>
     *      [32 new address]
     *      [96 signature]
     *      1 + 1 + 32 + 96 = 130
     *
     *      [32b subdomain address] - optional
     *      [96b signature] - optional
     *      [32b subdomain name in bytes] - optional
     *      130 + 32 + 32 + 32 = 226
     */
    @Override
    public ExecutionResult execute(byte[] input, long nrg) {
        // check for correct input length
        if (input.length != 130 && input.length != 226)
            return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);

        // declare variables for parsing the byte[] input and storing each value
        byte[] addressFirstPart = new byte[16];
        byte[] addressSecondPart = new byte[16];
        byte[] sign = new byte[96];
        byte[] subdomainAddress = new byte[32];
        byte[] domainNameInBytes = new byte[32];
        byte[] subdomainNameInBytes = new byte[32];
        String subdomainName = "";

        // process input data and store values, currently ignoring the chainID
        int offset = 0;
        offset++;
        byte operation = input[1];
        offset++;
        System.arraycopy(input, offset, addressFirstPart, 0, 16);
        offset += 16;
        System.arraycopy(input, offset, addressSecondPart, 0, 16);
        offset += 16;
        System.arraycopy(input, offset, sign, 0, 96);
        offset += 96;

        if (input.length == 226) {
            System.arraycopy(input, offset, subdomainAddress, 0, 32);
            offset += 32;
            System.arraycopy(input, offset, domainNameInBytes, 0, 32);
            offset += 32;
            System.arraycopy(input, offset, subdomainNameInBytes, 0, 32);

            try { // using explicit encoding for converting byte to string
                byte[] trimmedDomainNameInBytes = trimTrailingZeros(domainNameInBytes);
                byte[] trimmedSubdomainNameInBytes = trimTrailingZeros(subdomainNameInBytes);

                // store domain and subdomain name
                this.domainName = new String(trimmedDomainNameInBytes, "UTF-8");
                subdomainName = new String(trimmedSubdomainNameInBytes, "UTF-8");

                if(!isValidDomainName(this.domainName)){
                    return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);
                }

                domains.put(this.domainName, this.address);
            } catch (UnsupportedEncodingException a) {
                return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);
            }
        }

        // verify signature is correct
        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        byte[] data = new byte[32];
        System.arraycopy(ownerAddress.toBytes(), 0, data, 0, 32);

        boolean b = ECKeyEd25519.verify(data, sig.getSignature(), sig.getPubkey(null));
        if (!b) {
            return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // verify public key matches owner
        if (!this.ownerAddress.equals(Address.wrap(sig.getAddress()))) {
            return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // operation: {1-setResolver, 2-setTTL, 3-transferOwnership, 4-transferSubdomainOwnership}
        switch (operation) {
            case 1:
                byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
                byte[] resolverHash2 = blake128(resolverHash1);
                return setResolver(
                        resolverHash1, resolverHash2, addressFirstPart, addressSecondPart, nrg);
            case 2:
                byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
                byte[] TTLHash2 = blake128(TTLHash1);
                return setTTL(TTLHash1, TTLHash2, addressFirstPart, addressSecondPart, nrg);
            case 3:
                byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
                byte[] ownerHash2 = blake128(ownerHash1);
                return transferOwnership(
                        ownerHash1, ownerHash2, addressFirstPart, addressSecondPart, nrg);
            case 4:
                byte[] subownerHash1 = blake128(OWNER_HASH.getBytes());
                byte[] subownerHash2 = blake128(subownerHash1);
                return transferSubdomainOwnership(
                        subdomainAddress,
                        nrg,
                        subownerHash1,
                        subownerHash2,
                        addressFirstPart,
                        addressSecondPart,
                        subdomainName);
            default:
                return new ExecutionResult(
                        ResultCode.INTERNAL_ERROR, nrg); // unsupported operation
        }
    }

    /** Set Resolver for this domain */
    private ExecutionResult setResolver(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < SET_COST) return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.resolverAddressKey = new Address(combined);

        return new ExecutionResult(ResultCode.SUCCESS, nrg - SET_COST);
    }

    /** Set Time to Live for this domain */
    private ExecutionResult setTTL(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < SET_COST) return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.TTLKey = new Address(combined);

        return new ExecutionResult(ResultCode.SUCCESS, nrg - SET_COST);
    }

    /** Transfer the ownership of this domain */
    private ExecutionResult transferOwnership(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < TRANSFER_COST) return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        if (!isValidOwnerAddress(Address.wrap(combineTwoBytes(addr1, addr2))))
            return new ExecutionResult(ResultCode.INTERNAL_ERROR, nrg);

        Address.wrap(combineTwoBytes(addr1, addr2));
        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.ownerAddressKey = new Address(combined);

        return new ExecutionResult(ResultCode.SUCCESS, nrg - TRANSFER_COST);
    }

    /** Transfer the ownership of subdomain */
    private ExecutionResult transferSubdomainOwnership(
            byte[] subdomainAddress,
            long nrg,
            byte[] hash1,
            byte[] hash2,
            byte[] addr1,
            byte[] addr2,
            String subdomain) {
        if (nrg < TRANSFER_COST) return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        if (!isValidOwnerAddress(Address.wrap(combineTwoBytes(addr1, addr2))))
            return new ExecutionResult(ResultCode.INTERNAL_ERROR, nrg);

        Address sdAddress = Address.wrap(subdomainAddress);

        if (isSubdomain(subdomain)) {
            this.track.addStorageRow(sdAddress, new DataWord(hash1), new DataWord(addr1));
            this.track.addStorageRow(sdAddress, new DataWord(hash2), new DataWord(addr2));
            return new ExecutionResult(ResultCode.SUCCESS, nrg - TRANSFER_COST);
        }
        return new ExecutionResult(ResultCode.INTERNAL_ERROR, 0);
    }

    /**
     * Helper functions:
     *
     * processes on hashes, domain name, and addresses, converting, concatenating, partitioning
     *
     * data types: byte[], Address, Dataword, String
     */
    private void setUpKeys() {
        byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
        byte[] resolverHash2 = blake128(resolverHash1);

        byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
        byte[] TTLHash2 = blake128(TTLHash1);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        byte[] combined = combineTwoBytes(resolverHash1, resolverHash2);
        byte[] combined2 = combineTwoBytes(TTLHash1, TTLHash2);
        byte[] combined3 = combineTwoBytes(ownerHash1, ownerHash2);

        this.resolverAddressKey = new Address(combined);
        this.TTLKey = new Address(combined2);
        this.ownerAddressKey = new Address(combined3);
    }

    private byte[] combineTwoBytes(byte[] byte1, byte[] byte2) {
        byte[] combined = new byte[32];
        System.arraycopy(byte1, 0, combined, 0, 16);
        System.arraycopy(byte2, 0, combined, 16, 16);
        return combined;
    }

    private void storeResult(byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2) {
        this.track.addStorageRow(this.address, new DataWord(hash1), new DataWord(addr1));
        this.track.addStorageRow(this.address, new DataWord(hash2), new DataWord(addr2));
    }

    private Address getValueFromStorage(Address key) {
        if (key == null) return null;
        byte[] byteKey = key.toBytes();
        byte[] key1 = new byte[16];
        byte[] key2 = new byte[16];
        System.arraycopy(byteKey, 0, key1, 0, 16);
        System.arraycopy(byteKey, 16, key2, 0, 16);

        IDataWord data1 = this.track.getStorageValue(this.address, new DataWord(key1));
        IDataWord data2 = this.track.getStorageValue(this.address, new DataWord(key2));

        byte[] addr1 = data1.getData();
        byte[] addr2 = data2.getData();

        byte[] addrCombined = combineTwoBytes(addr1, addr2);
        return (new Address(addrCombined));
    }

    private void addToRegistered(Address domainAddress, String domainName){
        // set up domain name for storage
        byte[] domainNameInBytes = domainName.getBytes();
        byte[] domainNameInBytesWithLeadingZeros = addLeadingZeros(domainNameInBytes);
        String domainNameWithLeadingZeros =  new String(domainNameInBytesWithLeadingZeros);
        String fullDomainName = domainNameWithLeadingZeros.substring(0, 32);
        byte[] nameFirstPart = fullDomainName.substring(0, 16).getBytes();
        byte[] nameSecondPart = fullDomainName.substring(16, 32).getBytes();

        // set up domain address for storage
        byte[] addressFirstPart = new byte[16];
        byte[] addressSecondPart = new byte[16];
        System.arraycopy(domainAddress.toBytes(), 0, addressFirstPart, 0, 16);
        System.arraycopy(domainAddress.toBytes(), 16, addressSecondPart, 0, 16);

        //store
        // store name -> address pair
        this.track.addStorageRow(registeredDomainNameAddress, new DataWord(blake128(domainName.getBytes())), new DataWord(addressFirstPart));
        this.track.addStorageRow(registeredDomainNameAddress, new DataWord(blake128(blake128(domainName.getBytes()))), new DataWord(addressSecondPart));

        // store address -> name pair
        this.track.addStorageRow(registeredDomainAddressName, new DataWord(blake128(domainAddress.toBytes())), new DataWord(nameFirstPart));
        this.track.addStorageRow(registeredDomainAddressName, new DataWord(blake128(blake128(domainAddress.toBytes()))), new DataWord(nameSecondPart));
    }

    private boolean isSubdomain(String subdomainName) {
        String[] domainPartitioned = this.domainName.split("\\.");
        String[] subdomainPartitioned = subdomainName.split("\\.");

        if (domainPartitioned.length >= subdomainPartitioned.length) return false;

        for (int i = domainPartitioned.length-1, j = subdomainPartitioned.length-1; j >= subdomainPartitioned.length - domainPartitioned.length; i--, j--) {
            if (!domainPartitioned[i].equals(subdomainPartitioned[j])) return false;
        }
        return true;
    }

    private boolean isValidDomainAddress(Address domainAddress) {
        // checks if the given domain address is valid under the aion domain
        String rawAddress = domainAddress.toString();
        return rawAddress.charAt(0) == 'a' && rawAddress.charAt(1) == '0';
    }

    private boolean isValidOwnerAddress(Address ownerAddress) {
        // checks if the owner address is registered in the repository
        if (this.track.hasAccountState(ownerAddress)) return true;
        return (this.track.hasContractDetails(ownerAddress));
    }

    private boolean isValidDomainName(String domainName) {
        if (domains.containsKey(domainName))
            return domains.get(domainName).equals(this.address);
        return true; }

    private byte[] trimTrailingZeros(byte[] b) {
        if (b == null) return null;

        int counter = 0;
        for (int i = 0; i < 32; i++) {
            if (b[i] == 0) break;
            counter++;
        }

        byte[] ret = new byte[counter];
        System.arraycopy(b, 0, ret, 0, counter);
        return ret;
    }

    private boolean isAvailableDomain(Address domainAddress, Address ownerAddress){
        IDataWord addrFirstPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(domainAddress.toBytes())));
        IDataWord addrSecondPart = this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(blake128(domainAddress.toBytes()))));
        Address addrFromRepo = Address.wrap(combineTwoBytes(addrFirstPart.getData(), addrSecondPart.getData()));

        return addrFromRepo.equals(ownerAddress);
    }

    /** getter functions */
    public Address getResolverAddress() {
        return getValueFromStorage(this.resolverAddressKey);
    }

    public Address getTTL() {
        return getValueFromStorage(this.TTLKey);
    }

    public Address getOwnerAddress() {
        return getValueFromStorage(this.ownerAddressKey);
    }

    public Address getOwnerAddress(Address key) {
        return getValueFromStorage(key);
    }

    public static void clearDomainList() {
        domains.clear();
    }


    // query helpers
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

    private String getDomainNameFromAddress(Address domainAddress){
        String rawDomainName = "";
        IDataWord nameFirstPartData = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(domainAddress.toBytes())));
        IDataWord nameSecondPartData = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(blake128(domainAddress.toBytes()))));
        byte[] nameData = trimLeadingZeros(combineTwoBytes(nameFirstPartData.getData(), nameSecondPartData.getData()));
        try {
            rawDomainName = new String (nameData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String domainName = rawDomainName + ".aion";
        return domainName;
    }

    /**
     * Query Functions
     */
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
            if(!this.track.getStorageValue(activeDomainsAddress, new DataWord(blake128(tempDomainAddr.toBytes()))).equals(DoubleDataWord.ZERO)) {
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

    public void displayAllActiveDomains() {
        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();

        System.out.println("--------------------------AION NAME SERVICE QUERY: Active Domains (" + activeDomainsList.size() + ")-----------------------------");
        for (ActiveDomainsData domain: activeDomainsList){
            System.out.println("Domain name: " + domain.domainName);
            System.out.println("    Owner address: " + domain.ownerAddress);
            System.out.println("    Domain address: " + domain.domainAddress);
            System.out.println("    Expire Date: " + domain.expireDate);
            System.out.println("    Value: " + domain.auctionValue);
            if(domains.containsKey(domain.domainName))
                System.out.println("    Registered for aion name service: Yes");
            else
                System.out.println("    Registered for aion name service: No");
        }
        System.out.println();
    }

    public void displayMyDomains(ECKey key){
        Address callerAddress = Address.wrap(key.getAddress());
        System.out.println("----------------------------AION NAME SERVICE QUERY: displayMyDomains-----------------------------");

        if (!this.track.hasAccountState(callerAddress)){
            System.out.println("    The given account: " + callerAddress + " is not registered\n");
            return;
        }

        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();

        boolean notAnOwner = true;
        System.out.println("DOMAINS FOR ACCOUNT: " + callerAddress.toString());
        for(ActiveDomainsData domain: activeDomainsList){
            if(domain.ownerAddress.equals(callerAddress)){
                notAnOwner = false;
                System.out.println("Domain name: " + domain.domainName);
                System.out.println("    Owner address: " + domain.ownerAddress);
                System.out.println("    Domain address: " + domain.domainAddress);
                System.out.println("    Expire Date: " + domain.expireDate);
                System.out.println("    Value: " + domain.auctionValue);
                if(domains.containsKey(domain.domainName))
                    System.out.println("    Registered for aion name service: Yes");
                else
                    System.out.println("    Registered for aion name service: No");
            }
        }

        if (notAnOwner)
            System.out.println("    You do not own any domains\n");
        System.out.println();
    }

    public void displayRegisteredDomains(){
        List<ActiveDomainsData> activeDomainsList = getAllActiveDomains();
        int counter = 0;

        System.out.println("------------------------AION NAME SERVICE QUERY: Registered Active Domains --------------------------");
        System.out.println("List of active domains registered for ANS: ");
        for (ActiveDomainsData domain: activeDomainsList){
            if(domains.containsKey(domain.domainName)){
                System.out.println("    " + domain.domainAddress + ": " + domain.domainName);
                counter++;
            }
        }

        if (counter == 0)
            System.out.println("    Currently there are no active domains registered");

        System.out.println();
    }

    public String getRegisteredDomainName(Address domainAddress){
        byte[] domainNameFirstPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(domainAddress.toBytes()))).getData();
        byte[] domainNameSecondPart = this.track.getStorageValue(domainAddressNamePair, new DataWord(blake128(blake128(domainAddress.toBytes())))).getData();
        String domainName;
        try {
            domainName = new String(trimLeadingZeros(combineTwoBytes(domainNameFirstPart, domainNameSecondPart)), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            domainName = "";
        }

        if (domainName.equals(""))
            return null;

        domainName = domainName + ".aion";
        return domainName;
    }

    public Address getRegisteredDomainAddress(String domainName){
        byte[] addressFirstPart = this.track.getStorageValue(registeredDomainNameAddress, new DataWord(blake128(domainName.getBytes()))).getData();
        byte[] addressSecondPart = this.track.getStorageValue(registeredDomainNameAddress, new DataWord(blake128(blake128(domainName.getBytes())))).getData();
        Address domainAddress = Address.wrap(combineTwoBytes(addressFirstPart, addressSecondPart));
        if (domainAddress.equals(Address.ZERO_ADDRESS()))
            return null;
        return domainAddress;
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

}
