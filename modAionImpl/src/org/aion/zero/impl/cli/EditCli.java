package org.aion.zero.impl.cli;

import java.io.PrintStream;
import org.aion.db.impl.DBVendor;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.config.CfgApiRpc;
import org.aion.zero.impl.config.CfgApiZmq;
import org.aion.zero.impl.config.CfgConsensusUnity;
import org.aion.zero.impl.config.CfgDb;
import org.aion.zero.impl.config.CfgLog;
import org.aion.zero.impl.config.CfgNetP2p;
import org.aion.zero.impl.config.CfgSync;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine;

import static picocli.CommandLine.*;

@Command(name = "edit",
        aliases = {"e"},
        subcommands = {DevCLI.class},
        description = "Changes the kernel configuration at runtime. This sub-command needs to be issued after all options to \"aion.sh\" have been added.\nUse -h to view available options."
)
public class EditCli {

    @Option(
            names = {"--help", "-h"},
            arity = "0",
            description = "Print the help for the edit sub-command.")
    public Boolean help = false;

    @Option(names = {"port"},
            description = "Deploys the kernel with the specified port number.",
            paramLabel = "<setting>",
            arity = "1",
            converter = PortNumberConverter.class
    )
    private Integer port = null;
    @Option(names = {"state-storage"},
            paramLabel = "<setting>",
            description = "Changes the state pruning strategy.\nSettings: full, spread, and top.",
            converter = DBPruneOptionConverter.class,
            arity = "1"
    )
    private CfgDb.PruneOption pruneOption = null;
    @Option(names = {"internal-tx-storage", "itx"},
        paramLabel = "<setting>",
        description = "Enables/disables the storage of internal transactions.\nSettings: on / off.",
        converter = EnabledConverter.class,
        arity = "1"
    )
    private Boolean internalTxStorage = null;
    @Option(names = {"vendor"},
            paramLabel = "<setting>",
            description = "Changes the database implementation.\nSettings: h2, rocksdb, leveldb.\nNote that running different databases in the same directory will trigger erros.",
            converter = DBVendorConverter.class,
            arity = "1"
    )
    private DBVendor vendor = null;
    @Option(names = {"java"},
            paramLabel = "<setting>",
            description = "Enables/disables the Java API.\nSettings: on / off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean javaApi = null;
    @Option(names = {"rpc"},
            paramLabel = "<setting>",
            description = "Enables/disables the JSON RPC. Settings: on / off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean jsonRPC = null;
    @Option(names = {"mining"},
            paramLabel = "<setting>",
            description = "Enables/disables the internal CPU miner.\nSettings: on / off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean mining = null;
    @Option(names = "show-status",
            paramLabel = "<setting>",
            description = "Enables/disables the sync status log messages.\nSettings: on / off.",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean showStatus = null;
    @Option(names = {"compression"},
            paramLabel = "<setting>",
            description = "Enables/disables the database compression setting.\nSettings: on / off",
            converter = EnabledConverter.class,
            arity = "1"
    )
    private Boolean compression = null;
    @Option(names = {"log"},
            paramLabel = "<logger>=<loglevel>",
            description = "Set the log level of the specified logger.\nLoggers: API, CACHE, CONS, DB, EVTMGR, GEN, NET, P2P, ROOT, SURVEY, SYNC, TX, TXPOOL, VM.\nLoglevels: ERROR, WARN, INFO, DEBUG, TRACE.",
            converter = LogConverter.class,
            arity = "1..*"
    )
    private List<Object[]> log = null;

    private boolean updatePort(CfgNetP2p net) {
        if (port != null && net.getPort() != port) {
            net.setPort(port);
            System.out.println("Updated p2p port to: " + port);
            return true;
        } else {
            return false;
        }
    }

    private boolean updatePrune(CfgDb cfgDb) {
        if (pruneOption != null && !pruneOption.equals(cfgDb.getPrune_option())) {
            cfgDb.setPrune(pruneOption.toString());
            System.out.println("Updated state storage to: " + pruneOption.toString().toLowerCase());
            return true;
        } else return false;
    }

    private boolean updateInternalTx(CfgDb cfgDb) {
        if (internalTxStorage != null && internalTxStorage != cfgDb.isInternalTxStorageEnabled()) {
            cfgDb.setInternTxStorage(internalTxStorage);
            System.out.println(boolToMessage(internalTxStorage) + " storage of internal transactions.");
            return true;
        } else {
            return false;
        }
    }

    private boolean updateVendor(CfgDb cfgDb) {
        if (vendor != null && !vendor.equals(DBVendor.fromString(cfgDb.getVendor()))) {
            cfgDb.setVendor(vendor.name().toLowerCase());
            System.out.println("Updated database vendor to: " + vendor.toString().toLowerCase());
            return true;
        } else {
            return false;
        }
    }
    private static String boolToMessage(boolean bool){
        return bool? "Enabled":"Disabled";
    }

    private boolean updateJavaApi(CfgApiZmq cfgApiZmq) {
        if (javaApi !=null && javaApi != cfgApiZmq.getActive()) {
            cfgApiZmq.setActive(javaApi);
            System.out.println(boolToMessage(javaApi) + " Java API.");
            return true;
        } else {
            return false;
        }
    }

    private boolean updateJsonRPC(CfgApiRpc cfgApiRpc) {
        if (jsonRPC != null && jsonRPC != cfgApiRpc.isActive()) {
            cfgApiRpc.setActive(jsonRPC);
            System.out.println(boolToMessage(jsonRPC) + " JsonRPC.");
            return true;
        } else {
            return false;
        }
    }

    private boolean updateMining(CfgConsensusUnity cfgConsensus) {
        if (mining != null && mining != cfgConsensus.getMining()) {
            System.out.println(boolToMessage(mining) + " mining.");

            cfgConsensus.setMining(mining);
            return true;
        }

        return false;
    }

    private boolean updateStatus(CfgSync cfgSync) {
        if (showStatus != null && cfgSync.getShowStatus() != showStatus) {
            System.out.println((showStatus ? "Showing" : "Hiding") + " sync status.");
            cfgSync.setShowStatus(showStatus);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateCompression(CfgDb cfgDb) {
        if (compression != null && cfgDb.isCompression() != compression) {
            cfgDb.setCompression(compression);
            System.out.println(boolToMessage(compression) + " database compression.");
            return true;
        } else {
            return false;
        }
    }

    private boolean updateLog(CfgLog cfgLog) {
        boolean res = false;
        if (log != null) {
            for (Object[] objects : log) {
                if (cfgLog.updateModule((LogEnum) objects[0], (LogLevel) objects[1])) {
                    System.out.println("Changed log level of " + objects[0] + " logger to " + objects[1]);
                    res = true;
                }
            }
        }
        return res;
    }

    /** Prints the usage message for this command */
    public static void printUsage(PrintStream out, EditCli instance) {
        CommandLine parser = new CommandLine(instance);
        String usage = parser.getUsageMessage();
        // the additional dots are added for the styling extra characters
        usage = usage.replaceAll("=....<logger", " <logger");
        out.println(usage);
    }

    void checkOptions() {
        if (port == null &&
                pruneOption == null &&
                internalTxStorage == null &&
                vendor == null &&
                javaApi == null &&
                jsonRPC == null &&
                mining == null &&
                showStatus == null &&
                compression == null &&
                log == null &&
                help == false
        ) {
            throw new IllegalArgumentException("Expected an argument to the edit command");
        }
    }


    /**
     * @param cfg the config associated with the current runtime instance.
     * @return indicates whether any configs were changed
     */
    public boolean runCommand(CfgAion cfg) {
        checkOptions();
        if (help) {
            printUsage(System.out, this);
            return false;
        }
        //update all the configurations
        final boolean updateCompression = updateCompression(cfg.getDb());
        final boolean updateJavaApi = updateJavaApi(cfg.getApi().getZmq());
        final boolean updateJsonRPC = updateJsonRPC(cfg.getApi().getRpc());
        final boolean updateLog = updateLog(cfg.getLog());
        final boolean updateMining = updateMining(cfg.getConsensus());
        final boolean updatePort = updatePort(cfg.getNet().getP2p());
        final boolean updatePrune = updatePrune(cfg.getDb());
        final boolean updateInternalTx = updateInternalTx(cfg.getDb());
        final boolean updateStatus = updateStatus(cfg.getSync());
        final boolean updateVendor = updateVendor(cfg.getDb());

        //Indicate whether any of the configs were updated
        return updateCompression || updateJavaApi || updateJsonRPC || updateLog || updateMining || updatePort || updatePrune || updateInternalTx || updateStatus || updateVendor;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setPruneOption(CfgDb.PruneOption pruneOption) {
        this.pruneOption = pruneOption;
    }

    public void setInternalTxStorage(Boolean internalTxStorage) {
        this.internalTxStorage = internalTxStorage;
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
