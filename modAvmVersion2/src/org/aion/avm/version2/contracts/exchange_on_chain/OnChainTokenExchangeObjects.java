package org.aion.avm.version2.contracts.exchange_on_chain;

import avm.Address;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

public class OnChainTokenExchangeObjects {

    static class TokenUtility {

        String balanceMethodName;
        String transferMethodName;

        protected TokenUtility(String balanceMethodName, String transferMethodName) {
            this.balanceMethodName = balanceMethodName;
            this.transferMethodName = transferMethodName;
        }

        protected byte[] toBytes() {
            byte[] v1 = balanceMethodName.getBytes();
            byte[] v2 = transferMethodName.getBytes();
            int length = v1.length + v2.length;
            return AionBuffer.allocate(length).put(v1).put(v2).getArray();
        }

        protected static TokenUtility fromBytes(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new TokenUtility(new String(buffer.getArray()), new String(buffer.getArray()));
        }

        public byte[] callForBalance(Address account) {
            return new ABIStreamingEncoder()
                    .encodeOneString(balanceMethodName)
                    .encodeOneAddress(account)
                    .toBytes();
        }

        public byte[] callForTransfer(Address from, Address to, BigInteger amount) {
            return new ABIStreamingEncoder()
                    .encodeOneString(transferMethodName)
                    .encodeOneAddress(from)
                    .encodeOneAddress(to)
                    .encodeOneBigInteger(amount)
                    .toBytes();
        }
    }

    static class AccountData {

        Address address;
        List<Address> tokens;
        List<Integer> orders;

        protected AccountData(Address address, List<Address> tokens, List<Integer> orders) {
            this.address = address;
            this.tokens = tokens;
            this.orders = orders;
        }

        protected byte[] toBytes() {
            int length = Address.LENGTH + Address.LENGTH * tokens.size() + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            // add account
            aionBuffer.putAddress(address);
            // add tokens
            aionBuffer.putInt(tokens.size());
            for (Address token : tokens) {
                aionBuffer.putAddress(token);
            }
            // add orders
            aionBuffer.putInt(orders.size());
            for (Integer orderId : orders) {
                aionBuffer.putInt(orderId);
            }
            return aionBuffer.getArray();
        }

        protected static AccountData fromBytes(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            // read account address
            Address address = buffer.getAddress();
            // read tokens owned
            int tokensCount = buffer.getInt();
            List<Address> tokens = new ArrayList<>();
            for (int i = 0; i < tokensCount; i++) {
                tokens.add(buffer.getAddress());
            }
            // read orders made
            int ordersCount = buffer.getInt();
            List<Integer> orders = new ArrayList<>();
            for (int i = 0; i < ordersCount; i++) {
                orders.add(buffer.getInt());
            }
            return new AccountData(address, tokens, orders);
        }
    }

    static class Order {

        protected Order() {}

        protected byte[] toBytes() {
            return null;
        }

        protected static Order fromBytes(byte[] serializedBytes) {
            return new Order();
        }
    }
}
