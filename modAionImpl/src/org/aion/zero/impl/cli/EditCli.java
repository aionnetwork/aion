package org.aion.zero.impl.cli;

import org.aion.db.impl.DBVendor;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.config.*;
import org.aion.zero.impl.config.CfgConsensusPow;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

@Command(name = "edit",
        aliases = {"e"},
        description = "Changes the kernel configuration at runtime. This subcommand needs to be issued after " +
                "all options to \"aion.sh\" has been added." +
                "\nOptions\n" +
                "port=<setting> Executes the kernel with the specified port number.\n" +
                "state-storage=<setting> Changes the state pruning strategy. Settings: full, spread, and top.\n" +
                "vendor=<setting> Changes the database implementation. Settings: h2, rocksdb, and leveldb.\n" +
                "java=setting Enables/disables the java api. Settings on and off.\n" +
                "rpc=<setting> Enables/disables the json rpc. Settings on and off.\n" +
                "mining=<setting> Enables/disables the cpu miner. Settings on and off.\n" +
                "compression=<setting> Changes the database compression setting. Settings: on and off\n" +
                "log <logger>=<loglevel> Set the log level of the specified logger. Loggers: GEN, CONS, SYNC, API," +
                " VM, NET, DB, and P2P. Loglevels: INFO, DEBUG, ERROR, WARN, and TRACE."
)
public class EditCli {


    @Option(names = {"port"},
            description = "Executes the kernel with the specified port number.",
            paramLabel = "<setting>",
            arity = "1",
            converter = PortNumberConverter.class
    )
    private Integer port = null;
    @Option(names = {"state-storage"},
            paramLabel = "<setting>",
            description = "Changes the state pruning strategy. Settings: full, spread, and top.",
            converter = DBPruneOptionConverter.class,
            arity = "1"
    )
    private CfgDb.PruneOption pruneOption = null;
    @Option(names = {"vendor"},
            paramLabel = "<setting>",
            description = "Changes the database implementation. Settings: h2, rocksdb, leveldb.",
            converter = DBVendorConverter.class,
            arity = "1"
    )
    private DBVendor vendor = null;
    @Option(names = {"java"},
            paramLabel = "<setting>",
            description = "Enables/disables the java api. Settings on and off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean javaApi = null;
    @Option(names = {"rpc"},
            paramLabel = "<setting>",
            description = "Enables/disables the json rpc. Settings on and off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean jsonRPC = null;
    @Option(names = {"mining"},
            paramLabel = "<setting>",
            description = "Enables/disables the cpu miner. Settings on and off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean mining = null;
    @Option(names = "show-status",
            paramLabel = "<setting>",
            description = "Changes show/hides the sync status. Settings on and off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean showStatus = null;
    @Option(names = {"compression"},
            paramLabel = "<setting>",
            description = "Changes the database compression setting. Settings: on and off",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean compression = null;
    @Option(names = {"log"},
            paramLabel = "<logger>=<loglevel>",
            description = "Set the log level of the specified logger. Loggers: GEN, CONS, SYNC, API, VM, NET, DB, and P2P",
            converter = LogConverter.class,
            arity = "1..*"
    )
    private List<Object[]> log = null;

    private boolean updatePort(CfgNetP2p net) {
        if (port != null && net.getPort() != port) {
            net.setPort(port);
            return true;
        } else {
            return false;
        }
    }

    private boolean updatePrune(CfgDb cfgDb) {
        if (pruneOption != null && !pruneOption.equals(cfgDb.getPrune_option())) {
            cfgDb.setPrune_option(pruneOption);
            return true;
        } else return false;
    }

    private boolean updateVendor(CfgDb cfgDb) {
        if (vendor != null && !vendor.equals(DBVendor.fromString(cfgDb.getVendor()))) {
            cfgDb.setVendor(vendor.name());
            return true;
        } else {
            return false;
        }
    }

    private boolean updateJavaApi(CfgApiZmq cfgApiZmq) {
        if (javaApi !=null && javaApi != cfgApiZmq.getActive()) {
            cfgApiZmq.setActive(javaApi);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateJsonRPC(CfgApiRpc cfgApiRpc) {
        if (jsonRPC != null && jsonRPC != cfgApiRpc.isActive()) {
            cfgApiRpc.setActive(jsonRPC);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateMining(CfgConsensus cfgConsensus) {
        if (cfgConsensus instanceof CfgConsensusPow) {
            if (mining != null && mining != ((CfgConsensusPow) cfgConsensus).getMining()) {

                ((CfgConsensusPow) cfgConsensus).setMining(mining);
                return true;
            }
        }

        return false;
    }

    private boolean updateStatus(CfgSync cfgSync) {
        if (showStatus != null && cfgSync.getShowStatus() != showStatus) {
            cfgSync.setShowStatus(showStatus);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateCompression(CfgDb cfgDb) {
        if (compression != null && cfgDb.isCompression() != compression) {
            cfgDb.setCompression(compression);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateLog(CfgLog cfgLog) {
        boolean res = false;
        if (log != null) {
            for (Object[] objects : log) {

                if (cfgLog.updateModule(((LogEnum) objects[0]).name().toLowerCase(), ((LogLevel) objects[1]).name())) {
                    res = true;
                }
            }
        }
        return res;
    }

    void checkOptions() {
        if (port == null &&
                pruneOption == null &&
                vendor == null &&
                javaApi == null &&
                jsonRPC == null &&
                mining == null &&
                showStatus == null &&
                compression == null &&
                log == null
        ) {
            throw new IllegalArgumentException("Expected an argument to the edit command");
        }
    }


    /**
     * @param cfg the config associated with the current runtime instance.
     * @return indicates whether any configs were changed
     */
    public boolean runCommand(Cfg cfg) {
        checkOptions();
        //update all the configurations
        final boolean updateCompression = updateCompression(cfg.getDb());
        final boolean updateJavaApi = updateJavaApi(cfg.getApi().getZmq());
        final boolean updateJsonRPC = updateJsonRPC(cfg.getApi().getRpc());
        final boolean updateLog = updateLog(cfg.getLog());
        final boolean updateMining = updateMining(cfg.getConsensus());
        final boolean updatePort = updatePort(cfg.getNet().getP2p());
        final boolean updatePrune = updatePrune(cfg.getDb());
        final boolean updateStatus = updateStatus(cfg.getSync());
        final boolean updateVendor = updateVendor(cfg.getDb());

        //Indicate whether any of the configs were updated
        return updateCompression || updateJavaApi || updateJsonRPC || updateLog || updateMining || updatePort || updatePrune || updateStatus || updateVendor;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setPruneOption(CfgDb.PruneOption pruneOption) {
        this.pruneOption = pruneOption;
    }

    public void setVendor(DBVendor vendor) {
        this.vendor = vendor;
    }

    public void setJavaApi(Boolean javaApi) {
        this.javaApi = javaApi;
    }

    public void setJsonRPC(Boolean jsonRPC) {
        this.jsonRPC = jsonRPC;
    }

    public void setMining(Boolean mining) {
        this.mining = mining;
    }

    public void setShowStatus(Boolean showStatus) {
        this.showStatus = showStatus;
    }

    public void setCompression(Boolean compression) {
        this.compression = compression;
    }

    public void setLog(List<Object[]> log) {
        this.log = log;
    }

    public static class EnabledConverter implements ITypeConverter<Boolean> {
        @Override
        public Boolean convert(String value) {
            if (value.equals("on") || value.equals("off")) {
                return value.equals("on");
            } else {
                throw new IllegalArgumentException("Expected: on or off.");
            }
        }
    }

    public static class DBVendorConverter implements ITypeConverter<DBVendor> {

        @Override
        public DBVendor convert(String value) {
            try {
                DBVendor vendor = DBVendor.fromString(value.toLowerCase());

                if (vendor.equals(DBVendor.UNKNOWN)) {
                    throw new IllegalArgumentException();
                }
                return vendor;
            } catch (Exception e) {
                throw new IllegalArgumentException("Expected: h2, leveldb or rocksdb.");

            }
        }
    }

    public static class DBPruneOptionConverter implements ITypeConverter<CfgDb.PruneOption> {

        @Override
        public CfgDb.PruneOption convert(String value) {
            try {
                return CfgDb.PruneOption.valueOf(value.toUpperCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("Expected: " + Arrays.stream(CfgDb.PruneOption.values())
                        .map(CfgDb.PruneOption::toString)
                        .collect(Collectors.joining(", ")));
            }
        }
    }

    public static class LogConverter implements ITypeConverter<Object[]> {

        @Override
        public Object[] convert(String value) {
            try {
                String[] splitStrs = value.split("(\\s|=)");
                if (splitStrs.length == 2) {
                    return new Object[]{LogEnum.valueOf(splitStrs[0].toUpperCase()), LogLevel.valueOf(splitStrs[1].toUpperCase())};
                } else {
                    throw new IllegalArgumentException();
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Expected: <logger>=<loglevel>. Loggers=[%s]LogLevels=[%s].",
                        Arrays.stream(LogEnum.values()).map(LogEnum::toString).collect(Collectors.joining(", ")),
                        Arrays.stream(LogLevel.values()).map(LogLevel::toString).collect(Collectors.joining(","))
                ));
            }
        }
    }

    public static class PortNumberConverter implements ITypeConverter<Integer> {

        @Override
        public Integer convert(String value) {
            Integer val = Integer.parseInt(value);

            if (val > 0 && val <= 0xFFFF) {
                return val;
            } else throw new IllegalArgumentException("Invalid port number.");

        }
    }
}
