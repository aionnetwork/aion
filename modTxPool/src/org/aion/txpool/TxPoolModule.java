package org.aion.txpool;

import java.util.Properties;
import org.aion.base.type.ITransaction;

public final class TxPoolModule {
    private static TxPoolModule singleton = null;
    public static final String MODULENAME = "module_name";

    private static ITxPool<ITransaction> TXPOOL;

    @SuppressWarnings("unchecked")
    private TxPoolModule(Properties config) throws Exception {
        String moduleName = (String) config.get(MODULENAME);
        if (moduleName != null) {
            TXPOOL =
                    (ITxPool<ITransaction>)
                            getClass()
                                    .getClassLoader()
                                    .loadClass(moduleName)
                                    .getDeclaredConstructor(Properties.class)
                                    .newInstance(config);
        } else {
            throw new Exception("No module name input!");
        }
    }

    public static TxPoolModule getSingleton(Properties config) throws Exception {
        if (config == null) {
            throw new Exception("empty config!");
        }

        if (TxPoolModule.singleton == null) {
            TxPoolModule.singleton = new TxPoolModule(config);
        }

        return TxPoolModule.singleton;
    }

    public ITxPool<?> getTxPool() throws Exception {
        if (TxPoolModule.singleton == null) {
            throw new Exception("Module does not initialzed!");
        }

        return TxPoolModule.TXPOOL;
    }
}
