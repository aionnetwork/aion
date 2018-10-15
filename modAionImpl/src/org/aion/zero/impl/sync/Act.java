package org.aion.zero.impl.sync;

/** @author chris */
public final class Act {

    public static final byte REQ_STATUS = 0;

    public static final byte RES_STATUS = 1;

    public static final byte REQ_BLOCKS_HEADERS = 2;

    public static final byte RES_BLOCKS_HEADERS = 3;

    public static final byte REQ_BLOCKS_BODIES = 4;

    public static final byte RES_BLOCKS_BODIES = 5;

    public static final byte BROADCAST_TX = 6;

    public static final byte BROADCAST_BLOCK = 7;

    public static final byte REQUEST_TRIE_DATA = 8;

    public static final byte RESPONSE_TRIE_DATA = 9;

    public static final byte REQ_TX_RECEIPT_HEADERS = 10; // TODO ??

    public static final byte RES_TX_RECEIPT_HEADERS = 11; // TODO ??
}
