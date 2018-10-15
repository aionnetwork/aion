package org.aion.zero.impl.sync.handler;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.util.ByteUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResTxReceiptHandlerIntegTest {
    @Test
    public void test() {
        AionRepositoryImpl repo = AionRepositoryImpl.createForTesting(repoConfig);

        AionBlockStore fakeBlockstore = mock(AionBlockStore.class);
        AionBlock block = mock(AionBlock.class);
        List<AionTransaction> transactionsList = new LinkedList<>();
        when(fakeBlockstore.getBlockByHash(Mockito.any((byte[].class)))).thenReturn(block);
        when(block.getTransactionsList()).thenReturn(transactionsList);
        AionTransaction fakeTransaction = mock(AionTransaction.class);
        transactionsList.add(fakeTransaction);
        when(fakeTransaction.getHash()).thenReturn(
                ByteUtil.hexStringToBytes("7e21ba25b690afcb4e76adbb44b3147b30cd20969dffb9c252992fdcdaef9bc7")
        );

        ResTxReceiptHandler handler = new ResTxReceiptHandler(repo.getTransactionStore(), fakeBlockstore);

        System.out.println(String.format("BEFORE: %s",
            repo.getTransactionStore().get(ByteUtil.hexStringToBytes("7e21ba25b690afcb4e76adbb44b3147b30cd20969dffb9c252992fdcdaef9bc7"))
        ));

        byte[] msg = ByteUtil.hexStringToBytes("f90152f9014ff9012aa052e5c9cb4a615eae3963cbbc57a39ccea3ec9f47c1c742c6b3e276584710069bb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c08082520c80a03d62a9bd04d960329264248576d89cb00da7648819d2502a483a4002fc96299380");
        handler.receive(717142562, "33de43", msg);

        System.out.println(String.format("AFTER: %s",
                repo.getTransactionStore().get(ByteUtil.hexStringToBytes("7e21ba25b690afcb4e76adbb44b3147b30cd20969dffb9c252992fdcdaef9bc7"))
        ));
    }

    private IRepositoryConfig repoConfig =
            new IRepositoryConfig() {
                @Override
                public String getDbPath() {
                    return "/tmp/integ";
                }

                @Override
                public IPruneConfig getPruneConfig() {
                    return new CfgPrune(false);
                }

                @Override
                public IContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };
}
