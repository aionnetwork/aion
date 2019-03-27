package org.aion.util;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.db.impl.DatabaseFactory.Props.DB_NAME;
import static org.aion.db.impl.DatabaseTestUtils.getNext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DatabaseFactory;
import org.aion.db.impl.DatabaseTestUtils;
import org.aion.db.impl.DriverBenchmarkTest.RandomGenerator;
import org.aion.db.utils.FileUtils;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.log.AionLoggerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Records the time to put and get data into the database varying the size of the value.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class ValueSizeBenchmarkTest {
    private static final double compressionRatio = 0.5d;
    private static final int minValueSize = 2;
    private final RandomGenerator generator = new RandomGenerator(compressionRatio);;

    // set the desired log level
    static {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "ERROR");
        cfg.put("ROOT", "ERROR");

        AionLoggerFactory.init(cfg);
    }

    @SuppressWarnings("unused")
    private Object databaseInstanceDefinitions() {

        List<Object> parameters = new ArrayList<>();
        for (Properties dbDef :
                DatabaseTestUtils.unlockedPersistentDatabaseInstanceDefinitionsInternal()) {
            for (int i = 4; i < 18; i++) { // TODO: 31 max

                parameters.add(new Object[] {dbDef.clone(), (int) Math.pow(minValueSize, i)});
            }
        }
        return parameters;
    }

    /** Records the time to put and get data into the database varying the size of the value. */
    @Test
    @Parameters(method = "databaseInstanceDefinitions")
    public void fillValueIncreaseSize(Properties dbDef, int valueSize) {
        dbDef.setProperty(DB_NAME, "TestDB-" + getNext());

        int keyCount = (int) 1e4;

        // open database
        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();
        System.out.println(db + "\n");

        System.out.printf(
                "%10s, %20s, %20s, %20s, %20s, %20s, %20s \n",
                "iteration",
                "nb of keys/put",
                "value length",
                "time per put (sec)",
                "total time (sec)",
                "written bytes",
                "db file bytes");

        for (int iteration = 0; iteration < 10; iteration++) {
            double elapsedTime = 0d;
            long byteCount = 0l;
            byte[] key = new byte[] {(byte) iteration};

            for (long i = 0; i < keyCount; i++) {
                key = HashUtil.blake256Native(key);
                byte[] value = generator.generate(valueSize);

                long startTime = System.nanoTime();
                db.put(key, value);
                long endTime = System.nanoTime();

                byteCount += valueSize + key.length;
                elapsedTime += endTime - startTime;
            }

            // stop(name.getMethodName(), keyCount, valueSizeBytes,

            double elapsedSeconds = 1.0d * (elapsedTime) / TimeUnit.SECONDS.toNanos(1);

            // close the DB to get the right file size
            db.close();
            long fileSize = FileUtils.getDirectorySizeBytes(db.getPath().get());

            System.out.printf(
                    "%10d, %20d, %20d, %20.14f, %20.10f, %20d, %20d \n",
                    iteration,
                    keyCount,
                    valueSize,
                    elapsedSeconds / keyCount,
                    elapsedSeconds,
                    byteCount,
                    fileSize);

            // reset db for next test
            db.drop();
            assertThat(db.open()).isTrue();
        }

        db.close();
    }
}
