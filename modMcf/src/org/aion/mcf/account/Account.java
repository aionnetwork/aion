package org.aion.mcf.account;

import org.aion.crypto.ECKey;

/** Account class */
public class Account {

    private ECKey key;
    private long timeout;

    public Account(ECKey k, long t) {
        this.key = k;
        this.timeout = t;
    }

    public void updateTimeout(long t) {
        this.timeout = t;
    }

    public long getTimeout() {
        return this.timeout;
    }

    public ECKey getKey() {

        return this.key;
    }
}
