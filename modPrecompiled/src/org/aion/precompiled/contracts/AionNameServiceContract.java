/**
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 */
package org.aion.precompiled.contracts;

import static org.aion.crypto.HashUtil.blake128;

import java.util.HashMap;
import java.util.Map;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.base.vm.IDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;


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

    private Address address; // of contract
    private Address ownerAddress;
    private Address ownerAddressKey;
    private Address resolverAddressKey;
    private Address TTLKey;
    private String domainName;
    private Address contractKey;

    /** Construct a new ANS Contract */
    public AionNameServiceContract(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            Address address,
            Address ownerAddress,
            String domainName) { //byte
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
        if (!isValidDomainName(domainName)) {
            throw new IllegalArgumentException(
                    "An ANS contract with domain name: \"" + domainName + "\" already exists");
        }
        domains.put(domainName, address);
        this.ownerAddress = ownerAddress;
        this.domainName = domainName;
    }

    /**
     * input is defined as: [ <1b chainID> |<1b operation> | <32b address> | <96b signature>] total:
     * 1 + 1 + 32 + 96 = 130
     *
     * <p>input for operation on subdomain [ <1b chainID> |<1b operation> | <32b address> | <96b
     * signature> | <32b subdomain address] total: 1 + 1 + 32 + 96 + 32 = 162
     *
     * <p>Where the chainId is intended to be our current chainId, in the case of the first AION
     * network this should be set to 1. operation checks for which contract operation the user
     * wishes to execute. The address represent the new address to be used, and lastly the signature
     * for security.
     */
    public ContractExecutionResult execute(byte[] input, long nrg) {
        return execute(input, nrg, "");
    }

    public ContractExecutionResult execute(byte[] input, long nrg, String subdomain) {

        // check for correct input format
        if (input.length != 130 && input.length != 162)
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);

        // process input data
        int offset = 0;
        // DataWord chainId = new DataWord(input[0]);
        offset++;
        byte operation = input[1];
        offset++;

        byte[] addressFirstPart = new byte[16];
        byte[] addressSecondPart = new byte[16];
        byte[] sign = new byte[96];
        byte[] subdomainAddress = new byte[32];

        System.arraycopy(input, offset, addressFirstPart, 0, 16);
        offset += 16;
        System.arraycopy(input, offset, addressSecondPart, 0, 16);
        offset += 16;
        System.arraycopy(input, offset, sign, 0, 96);
        offset += 96;

        // verify signature is correct
        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        // if (sig == null){
        //    return  new ExecutionResult(ResultCode.INTERNAL_ERROR, nrg);
        // }

        byte[] payload = new byte[34];
        System.arraycopy(input, 0, payload, 0, 34);
        boolean b = ECKeyEd25519.verify(payload, sig.getSignature(), sig.getPubkey(null));

        if (!b) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // verify public key matches owner
        if (!this.ownerAddress.equals(Address.wrap(sig.getAddress()))) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
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
                System.arraycopy(input, offset, subdomainAddress, 0, 32);
                return transferSubdomainOwnership(
                        subdomainAddress,
                        nrg,
                        subownerHash1,
                        subownerHash2,
                        addressFirstPart,
                        addressSecondPart,
                        subdomain);
            default:
                return new ContractExecutionResult(
                        ResultCode.INTERNAL_ERROR, nrg); // unsupported operation
        }
    }

    /** Set Resolver for this domain */
    private ContractExecutionResult setResolver(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < SET_COST) return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);

        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.resolverAddressKey = new Address(combined);

        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - SET_COST);
    }

    /** Set Time to Live for this domain */
    private ContractExecutionResult setTTL(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < SET_COST) return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);

        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.TTLKey = new Address(combined);

        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - SET_COST);
    }

    /** Transfer the ownership of this domain */
    private ContractExecutionResult transferOwnership(
            byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg) {
        if (nrg < TRANSFER_COST) return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);

        if (!isValidOwnerAddress(Address.wrap(combineTwoBytes(addr1, addr2))))
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg);

        Address.wrap(combineTwoBytes(addr1, addr2));
        storeResult(hash1, hash2, addr1, addr2);

        // set the key
        byte[] combined = combineTwoBytes(hash1, hash2);
        this.ownerAddressKey = new Address(combined);

        return new ContractExecutionResult(ResultCode.SUCCESS, nrg - TRANSFER_COST);
    }

    /** Transfer the ownership of subdomain */
    private ContractExecutionResult transferSubdomainOwnership(
            byte[] subdomainAddress,
            long nrg,
            byte[] hash1,
            byte[] hash2,
            byte[] addr1,
            byte[] addr2,
            String subdomain) {
        if (nrg < TRANSFER_COST) return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);

        if (!isValidOwnerAddress(Address.wrap(combineTwoBytes(addr1, addr2))))
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, nrg);

        Address sdAddress = Address.wrap(subdomainAddress);

        if (isSubdomain(subdomain)) {
            this.track.addStorageRow(sdAddress, new DataWord(hash1), new DataWord(addr1));
            this.track.addStorageRow(sdAddress, new DataWord(hash2), new DataWord(addr2));
            return new ContractExecutionResult(ResultCode.SUCCESS, nrg - TRANSFER_COST);
        }
        return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
    }

    /**
     * Helper functions:
     *
     * <p>processes on hashes, domain name, and addresses, converting, concatenating, partitioning
     *
     * <p>data types: byte[], Address, Dataword, String
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

    private boolean isSubdomain(String subdomainName) {
        String[] domainPartitioned = this.domainName.split("\\.");
        String[] subdomainPartitioned = subdomainName.split("\\.");

        if (domainPartitioned.length >= subdomainPartitioned.length) return false;

        for (int i = 0; i < domainPartitioned.length; i++) {
            if (!domainPartitioned[i].equals(subdomainPartitioned[i])) return false;
        }
        return true;
    }

    // checks if the given domain address is valid under the aion domain
    private boolean isValidDomainAddress(Address domainAddress) {
        String rawAddress = domainAddress.toString();
        return rawAddress.charAt(0) == 'a' && rawAddress.charAt(1) == '0';
    }

    // checks if the owner address is registered in the repository
    private boolean isValidOwnerAddress(Address ownerAddress) {
        if (this.track.hasAccountState(ownerAddress)) return true;
        return (this.track.hasContractDetails(ownerAddress));
    }

    private boolean isValidDomainName(String domainName) {
        if (domains.containsKey(domainName)) return false;
        return true;
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
}
