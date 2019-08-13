package org.aion.zero.impl.cli;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.db.impl.DBVendor;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.config.CfgDb;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.util.TestResources.TEST_RESOURCE_DIR;

@RunWith(JUnitParamsRunner.class)
public class EditCliTest {

    private final CfgAion cfg = CfgAion.inst();
    private static final String configFileName = "config.xml";
    private static final File config = new File(TEST_RESOURCE_DIR, configFileName);


    @After
    public void tearDown(){
        cfg.resetInternal();
        cfg.fromXML(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyCLI(){
        new EditCli().runCommand(cfg);
    }


    @Parameters(method = "updateCommandParams")
    @Test
    public void testUpdateCommand(Integer port,
                                  CfgDb.PruneOption prune,
                                  DBVendor vendor,
                                  Boolean javaAPI,
                                  Boolean jsonRPC,
                                  Boolean mining,
                                  Boolean showStatus,
                                  Boolean compression,
                                  Boolean internalTxStorage,
                                  Object[] objects){

        EditCli cli = new EditCli();
        List<Object[]> logs;
        //convert the object array to a list
        if (objects.length ==2){
            logs = Collections.singletonList(objects);
        }
        else {

            logs = new ArrayList<>();
            for (int i = 0; i < objects.length; i+=2){
                logs.add(new Object[]{objects[i], objects[i+1]});
            }
        }
        cli.setCompression(compression);
        cli.setJavaApi(javaAPI);
        cli.setJsonRPC(jsonRPC);
        cli.setLog(logs);
        cli.setMining(mining);
        cli.setPruneOption(prune);
        cli.setInternalTxStorage(internalTxStorage);
        cli.setShowStatus(showStatus);
        cli.setVendor(vendor);
        cli.setPort(port);
        assertThat(cli.runCommand(cfg)).isTrue();


        //check that the changes were applied
        assertThat(cfg.getDb().isCompression()).isEqualTo(compression);
        assertThat(cfg.getConsensus().getMining()).isEqualTo(mining);
        assertThat(cfg.getApi().getRpc().isActive()).isEqualTo(jsonRPC);
        assertThat(cfg.getApi().getZmq().getActive()).isEqualTo(javaAPI);
        assertThat(cfg.getDb().getVendor()).ignoringCase().isEqualTo(vendor.name());
        assertThat(cfg.getDb().getPrune_option()).isEqualTo(prune);
        assertThat(cfg.getDb().isInternalTxStorageEnabled()).isEqualTo(internalTxStorage);
        assertThat(cfg.getNet().getP2p().getPort()).isEqualTo(port);
        assertThat(cfg.getSync().getShowStatus()).isEqualTo(showStatus);

        for (Object[] arr: logs){
            assertThat(cfg.getLog().getModules()).containsEntry(arr[0].toString(),arr[1].toString());
        }
    }


    public Object[] updateCommandParams(){
        return new Object[] {
                new Object[]{30300, CfgDb.PruneOption.TOP, DBVendor.H2, false, false, false, false, false, true, new Object[]{LogEnum.API, LogLevel.DEBUG}},
                new Object[]{30301, CfgDb.PruneOption.SPREAD, DBVendor.LEVELDB, true, true, true, true, true, false, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.SPREAD, DBVendor.LEVELDB, true, true, true, true, true, true, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.LEVELDB, true, true, true, true, true, false, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, true, true, true, true, true, true, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, true, true, true, true, false, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, true, true, true, true, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, true, true, false, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, true, true, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, false, new Object[]{LogEnum.API, LogLevel.INFO}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, true, new Object[]{LogEnum.API, LogLevel.DEBUG}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, false, new Object[]{LogEnum.GEN, LogLevel.DEBUG,LogEnum.SYNC, LogLevel.DEBUG}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, true, new Object[]{LogEnum.GEN, LogLevel.DEBUG,LogEnum.SYNC, LogLevel.DEBUG,LogEnum.DB, LogLevel.DEBUG,LogEnum.CONS, LogLevel.DEBUG}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, false, new Object[]{LogEnum.GEN, LogLevel.DEBUG,LogEnum.SYNC, LogLevel.DEBUG,LogEnum.DB, LogLevel.DEBUG,LogEnum.CONS, LogLevel.DEBUG,LogEnum.P2P, LogLevel.DEBUG,LogEnum.VM, LogLevel.DEBUG}},
                new Object[]{30302, CfgDb.PruneOption.TOP, DBVendor.ROCKSDB, false, false, false, false, false, true, new Object[]{LogEnum.GEN, LogLevel.INFO,LogEnum.SYNC, LogLevel.INFO,LogEnum.DB, LogLevel.INFO,LogEnum.CONS, LogLevel.INFO,LogEnum.P2P, LogLevel.INFO,LogEnum.VM, LogLevel.INFO}}
        };
    }

    @Parameters(method = "paramsForTestConverter")
    @Test
    public void testConverter(String option, CommandLine.ITypeConverter typeConverter, Object expected) throws Exception {

        assertThat(typeConverter.convert(option)).isEqualTo(expected);
    }


    @Parameters(method = "paramsForTestConverterValidation")
    @Test(expected = RuntimeException.class)
    public void testConverterValidation(String option, CommandLine.ITypeConverter typeConverter) throws Exception {

        typeConverter.convert(option);
    }


    public Object paramsForTestConverter(){
        List<Object> params = new ArrayList<>();
        EditCli.EnabledConverter enabledConverter = new EditCli.EnabledConverter();
        EditCli.DBVendorConverter dbVendorConverter = new EditCli.DBVendorConverter();
        EditCli.DBPruneOptionConverter dbPruneOptionConverter = new EditCli.DBPruneOptionConverter();
        EditCli.PortNumberConverter portNumberConverter = new EditCli.PortNumberConverter();
        EditCli.LogConverter logConverter = new EditCli.LogConverter();
        params.add(new Object[]{"on", enabledConverter, true});
        params.add(new Object[]{"off", enabledConverter, false});

        for (DBVendor vendor: DBVendor.values()){
            if (!vendor.equals(DBVendor.UNKNOWN)) {
                params.add(new Object[]{vendor.toString().toUpperCase(), dbVendorConverter, vendor});
                params.add(new Object[]{vendor.toString().toLowerCase(), dbVendorConverter, vendor});
            }
        }

        for (CfgDb.PruneOption pruneOption: CfgDb.PruneOption.values()){
            params.add(new Object[]{pruneOption.toString().toUpperCase(), dbPruneOptionConverter, pruneOption });
            params.add(new Object[]{pruneOption.toString().toLowerCase(), dbPruneOptionConverter, pruneOption });
        }

        for (LogEnum logEnum: LogEnum.values()){
            for (LogLevel logLevel: LogLevel.values()){
                params.add(new Object[]{logEnum.toString()+"=" + logLevel.toString(), logConverter, new Object[]{logEnum, logLevel}});
            }
        }

        int[] ports = {0x1, 0xF, 0xFF, 0xFFF, 0xFFFF};

        for (Integer port: ports){
            params.add(new Object[]{port.toString(), portNumberConverter, port});
        }

        return params;
    }


    public Object paramsForTestConverterValidation(){
        List<Object[]> params = new ArrayList<>();
        EditCli.EnabledConverter enabledConverter = new EditCli.EnabledConverter();
        EditCli.DBVendorConverter dbVendorConverter = new EditCli.DBVendorConverter();
        EditCli.DBPruneOptionConverter dbPruneOptionConverter = new EditCli.DBPruneOptionConverter();
        EditCli.PortNumberConverter portNumberConverter = new EditCli.PortNumberConverter();
        EditCli.LogConverter logConverter = new EditCli.LogConverter();
        params.add(new Object[]{"onn", enabledConverter});
        params.add(new Object[]{"offf", enabledConverter});
        params.add(new Object[]{"on".toUpperCase(), enabledConverter});
        params.add(new Object[]{"off".toUpperCase(), enabledConverter});
        params.add(new Object[]{"".toUpperCase(), enabledConverter});

        for (DBVendor vendor: DBVendor.values()) {

            params.add(new Object[]{vendor +"1", dbVendorConverter});
        }

        params.add(new Object[]{"", dbVendorConverter});

        for (CfgDb.PruneOption pruneOption: CfgDb.PruneOption.values()){
            params.add(new Object[]{pruneOption.toString().toUpperCase()+"1", dbPruneOptionConverter });
            params.add(new Object[]{pruneOption.toString().toLowerCase()+"1", dbPruneOptionConverter });
        }
        params.add(new Object[]{"", dbPruneOptionConverter});

        for (LogEnum logEnum: LogEnum.values()){
            for (LogLevel logLevel: LogLevel.values()){
                params.add(new Object[]{logEnum.toString()+"-" + logLevel.toString(), logConverter});
                params.add(new Object[]{logEnum.toString()+"=" + logLevel.toString()+"1", logConverter});
                params.add(new Object[]{logEnum.toString()+"1"+"=" + logLevel.toString()+"1", logConverter});
                params.add(new Object[]{logEnum.toString()+"1"+"=" + logLevel.toString(), logConverter});
            }
        }
        params.add(new Object[]{"", logConverter});




        int[] ports = {0x0, 0xFFFF+1};

        for (Integer port: ports){
            params.add(new Object[]{port.toString(), portNumberConverter});
        }
        params.add(new Object[]{"", portNumberConverter});
        return params;
    }


}