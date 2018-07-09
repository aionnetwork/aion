package org.aion.db;

import static org.junit.Assert.*;

import java.util.Random;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.junit.Test;

/**
 * Tests the DoubleDataWord class, mainly that it integrates well with the db.
 */
public class DoubleDataWordTest {
    private IRepository repo = AionRepositoryImpl.inst();
    private IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track = new AionRepositoryCache(repo);
    private Random rand = new Random();
    private Address addr = Address.wrap(ECKeyFac.inst().create().getAddress());

    /**
     * Tests that a using a single key that is a prefix of a double key and also a single key that
     * is a suffix of a double key (first 16 and last 16 bytes matching) will not cause ambiguity
     * and the db will fetch and store the correct entries.
     */
    @Test
    public void testPrefixSuffixAmbiguity() {
        // doubleKeyFirst has its first 16 bytes identical to singleKey
        // doubleKeyLast has its last 16 bytes identical to singleKey
        byte[] singleKey = new byte[DataWord.BYTES];
        byte[] doubleKeyFirst = new byte[DoubleDataWord.BYTES];
        byte[] doubleKeyLast = new byte[DoubleDataWord.BYTES];
        rand.nextBytes(singleKey);
        System.arraycopy(singleKey, 0, doubleKeyFirst, 0, DataWord.BYTES);
        System.arraycopy(singleKey, 0, doubleKeyLast, DataWord.BYTES, DataWord.BYTES);

        byte[] singleVal = new byte[DataWord.BYTES];
        byte[] doubleValFirst = new byte[DoubleDataWord.BYTES];
        byte[] doubleValLast = new byte[DoubleDataWord.BYTES];
        singleVal[0] = (byte) 0x1;
        doubleValFirst[0] = (byte) 0x2;
        doubleValLast[0] = (byte) 0x3;

        track.addStorageRow(addr, new DataWord(singleKey), new DataWord(singleVal));
        track.addStorageRow(addr, new DoubleDataWord(doubleKeyFirst), new DoubleDataWord(doubleValFirst));
        track.addStorageRow(addr, new DoubleDataWord(doubleKeyLast), new DoubleDataWord(doubleValLast));
        track.flush();

        byte[] singleRes = track.getStorageValue(addr, new DataWord(singleKey)).getData();
        byte[] doubleResFirst = track.getStorageValue(addr, new DoubleDataWord(doubleKeyFirst)).getData();
        byte[] doubleResLast = track.getStorageValue(addr, new DoubleDataWord(doubleKeyLast)).getData();

        assertArrayEquals(singleVal, singleRes);
        assertArrayEquals(doubleValFirst, doubleResFirst);
        assertArrayEquals(doubleValLast, doubleResLast);
    }

    /**
     * Tests that when we add a single key to the db that then we cannot use double keys that are
     * prefixes or suffixes of the single key to confuse the db and get the single entry.
     */
    @Test
    public void testPrefixSuffixAmbiguity2() {
        byte[] singKey = new byte[DataWord.BYTES];
        rand.nextBytes(singKey);

        byte[] singVal = new byte[DataWord.BYTES];
        singVal[0] = (byte) 0xAC;
        track.addStorageRow(addr, new DataWord(singKey), new DataWord(singVal));
        track.flush();

        byte[] doubleKeyPrefix = new byte[DoubleDataWord.BYTES];
        byte[] doubleKeySuffix = new byte[DoubleDataWord.BYTES];
        System.arraycopy(singKey, 0, doubleKeyPrefix, 0, DataWord.BYTES);
        System.arraycopy(singKey, 0, doubleKeySuffix, DataWord.BYTES, DataWord.BYTES);

        byte[] singRes = track.getStorageValue(addr, new DataWord(singKey)).getData();
        assertArrayEquals(singVal, singRes);
        assertNull(track.getStorageValue(addr, new DoubleDataWord(doubleKeyPrefix)));
        assertNull(track.getStorageValue(addr, new DoubleDataWord(doubleKeySuffix)));
    }

    /**
     * Tests that the key-value pairs added to the db do not both have to be singles or both doubles
     * but that we can have single-double and double-single pairings too.
     */
    @Test
    public void testMixOfSingleAndDoubleDataWordsInRepo() {
        byte[] key16 = new byte[DataWord.BYTES];
        byte[] key32 = new byte[DoubleDataWord.BYTES];
        byte[] val16 = new byte[DataWord.BYTES];
        byte[] val32 = new byte[DoubleDataWord.BYTES];
        rand.nextBytes(key16);
        rand.nextBytes(key32);
        rand.nextBytes(val16);
        rand.nextBytes(val32);

        track.addStorageRow(addr, new DataWord(key16), new DoubleDataWord(val32));
        track.addStorageRow(addr, new DoubleDataWord(key32), new DataWord(val16));

        assertArrayEquals(val16, track.getStorageValue(addr, new DoubleDataWord(key32)).getData());
        assertArrayEquals(val32, track.getStorageValue(addr, new DataWord(key16)).getData());
    }

}
