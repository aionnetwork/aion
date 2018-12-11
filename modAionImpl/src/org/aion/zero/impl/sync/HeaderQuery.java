package org.aion.zero.impl.sync;

/** @author chris */
final class HeaderQuery {

    String fromNode;

    long from;

    int take;

    HeaderQuery(String _fromNode, long _from, int _take) {
        this.fromNode = _fromNode;
        this.from = _from;
        this.take = _take;
    }
}
