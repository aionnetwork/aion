package org.aion.zero.impl.config;

import com.google.common.base.Objects;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgApi;
import org.aion.mcf.config.CfgDb;
import org.aion.mcf.config.CfgFork;
import org.aion.mcf.config.CfgGui;
import org.aion.mcf.config.CfgLog;
import org.aion.mcf.config.CfgNet;
import org.aion.mcf.config.CfgReports;
import org.aion.mcf.config.CfgSync;
import org.aion.mcf.config.CfgTx;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.types.GenesisBlockLoader;
import org.aion.zero.impl.types.GenesisStakingBlock;

/** @author chris */
public final class CfgAion extends Cfg {

    protected AionGenesis genesis;

    protected static final int N = 210;

    private static final int K = 9;

    private static final String NODE_ID_PLACEHOLDER = "[NODE-ID-PLACEHOLDER]";

    public CfgAion() {
        this.mode = "aion";
        this.id = UUID.randomUUID().toString();
        this.keystorePath = null;
        this.net = new CfgNet();
        this.consensus = new CfgConsensusUnity();
        this.sync = new CfgSync();
        this.api = new CfgApi();
        this.db = new CfgDb();
        this.log = new CfgLog();
        this.tx = new CfgTx();
        this.reports = new CfgReports();
        this.gui = new CfgGui();
        this.fork = new CfgFork();
        initializeConfiguration();
    }

    private static class CfgAionHolder {
        private static CfgAion inst = new CfgAion();
    }

    public static CfgAion inst() {
        return CfgAionHolder.inst;
    }

    public static void setInst(CfgAion cfgAion) {
        CfgAionHolder.inst = cfgAion;
    }

    @Override
    public void setGenesis() {
        try {
            this.genesis = GenesisBlockLoader.loadJSON(getInitialGenesisFile().getAbsolutePath());
        } catch (IOException e) {
            System.out.println(String.format("Genesis load exception %s", e.getMessage()));
            System.out.println("defaulting to default AionGenesis configuration");
            try {
                this.genesis = (new AionGenesis.Builder()).build();
            } catch (Exception e2) {
                // if this fails, it means our DEFAULT genesis violates header rules
                // this is catastrophic
                System.out.println("load default AionGenesis runtime failed! " + e2.getMessage());
                throw new RuntimeException(e2);
            }
        }
    }

    public void setGenesis(AionGenesis genesis) {
        this.genesis = genesis;
    }

    public CfgConsensusUnity getConsensus() {
        return (CfgConsensusUnity) this.consensus;
    }

    public AionGenesis getGenesis() {
        if (this.genesis == null) setGenesis();
        return this.genesis;
    }

    public GenesisStakingBlock getGenesisStakingBlock() {

        // We need the extraData from the PoWGenesis block
        if (genesis == null) {
            setGenesis();
        }

        return genesis.getGenesisStakingBlock();
    }

    public static int getN() {
        return N;
    }

    public static int getK() {
        return K;
    }

    private void closeFileInputStream(final FileInputStream fis) {
        if (fis != null) {
            try {
                fis.close();
            } catch (IOException e) {
                System.out.println("<error on-close-file-input-stream>");
                System.exit(SystemExitCodes.INITIALIZATION_ERROR);
            }
        }
    }

    //    /** @implNote the default fork settings is looking for the fork config of the mainnet. */
    //    public void setForkProperties() {
    //        setForkProperties("mainnet", null);
    //    }

    public void setForkProperties(String networkName, File forkFile) {
        Properties properties = new Properties();

        // old kernel doesn't support the fork feature.
        if (networkName == null || networkName.equals("config")) {
            return;
        }

        try (FileInputStream fis =
                (forkFile == null)
                        ? new FileInputStream(
                                System.getProperty("user.dir")
                                        + "/"
                                        + networkName
                                        + "/config"
                                        + CfgFork.FORK_PROPERTIES_PATH)
                        : new FileInputStream(forkFile)) {

            properties.load(fis);
            this.getFork().setProperties(properties);
        } catch (Exception e) {
            System.out.println(
                    "<error on-parsing-fork-properties msg="
                            + e.getLocalizedMessage()
                            + ">, no protocol been updated.");
        }
    }

    //    public void setForkProperties(String networkName) {
    //        setForkProperties(networkName, null);
    //    }

    public void dbFromXML() {
        File cfgFile = getInitialConfigFile();
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            loop:
            while (sr.hasNext()) {
                int eventType = sr.next();
                switch (eventType) {
                    case XMLStreamReader.START_ELEMENT:
                        String elementName = sr.getLocalName().toLowerCase();
                        switch (elementName) {
                            case "db":
                                this.db.fromXML(sr);
                                break;
                            default:
                                skipElement(sr);
                                break;
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        if (sr.getLocalName().toLowerCase().equals("aion")) break loop;
                        else break;
                }
            }
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        } finally {
            closeFileInputStream(fis);
        }
    }

    public boolean fromXML(final XMLStreamReader sr) throws XMLStreamException {
        boolean shouldWriteBackToFile = false;
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "id":
                            String nodeId = readValue(sr);
                            if (NODE_ID_PLACEHOLDER.equals(nodeId)) {
                                this.id = UUID.randomUUID().toString();
                                shouldWriteBackToFile = true;
                            } else {
                                this.id = nodeId;
                            }
                            break;
                        case "mode":
                            this.mode = readValue(sr);
                            break;
                        case "api":
                            this.api.fromXML(sr);
                            break;
                        case "net":
                            this.net.fromXML(sr);
                            break;
                        case "sync":
                            this.sync.fromXML(sr);
                            break;
                        case "consensus":
                            this.consensus.fromXML(sr);
                            break;
                        case "db":
                            this.db.fromXML(sr);
                            break;
                        case "log":
                            this.log.fromXML(sr);
                            break;
                        case "tx":
                            this.tx.fromXML(sr);
                            break;
                        case "reports":
                            this.reports.fromXML(sr);
                            break;
                        case "gui":
                            this.gui.fromXML(sr);
                            break;
                        default:
                            skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    if (sr.getLocalName().toLowerCase().equals("aion")) break loop;
                    else break;
            }
        }
        return shouldWriteBackToFile;
    }

    @Override
    public boolean fromXML() {
        return fromXML(getInitialConfigFile());
    }

    @Override
    public boolean fromXML(File cfgFile) {
        boolean shouldWriteBackToFile = false;
        if (!cfgFile.exists()) {
            return false;
        }
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            shouldWriteBackToFile = fromXML(sr);
            closeFileInputStream(fis);
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }

        // checks for absolute path for database
        File db = new File(this.getDb().getPath());
        if (db.isAbsolute()) {
            this.setDatabaseDir(db);
        }

        // checks for absolute path for log
        File log = new File(this.getLog().getLogPath());
        if (log.isAbsolute()) {
            this.setLogDir(log);
        }

        if (keystorePath != null) {
            File ks = new File(keystorePath);
            if (ks.isAbsolute()) {
                this.setKeystoreDir(ks);
            }
        }

        return shouldWriteBackToFile;
    }

    @Override
    public void toXML(final String[] args) {
        toXML(args, getExecConfigFile());
    }

    @Override
    public void toXML(final String[] args, File file) {
        if (args != null) {
            boolean override = false;
            for (String arg : args) {
                arg = arg.toLowerCase();
                if (arg.startsWith("--id=")) {
                    override = true;
                    String id = arg.replace("--id=", "");
                    try {
                        UUID uuid = UUID.fromString(id);
                        this.id = uuid.toString();
                    } catch (IllegalArgumentException exception) {
                        System.out.println("<invalid-id-arg id=" + id + ">");
                    }
                }
                if (arg.startsWith("--nodes=")) {
                    override = true;
                    String[] subArgsArr = arg.replace("--nodes=", "").split(",");
                    if (subArgsArr.length > 0) {
                        List<String> _nodes = new ArrayList<>();
                        for (String subArg : subArgsArr) {
                            if (!subArg.equals("")) {
                                _nodes.add(subArg);
                            }
                        }
                        this.getNet().setNodes(_nodes.toArray(new String[0]));
                    }
                }
                if (arg.startsWith("--p2p=")) {
                    override = true;
                    String[] subArgsArr = arg.replace("--p2p=", "").split(",");
                    if (subArgsArr.length == 2) {
                        if (!subArgsArr[0].equals("")) {
                            this.getNet().getP2p().setIp(subArgsArr[0]);
                        }
                        this.getNet().getP2p().setPort(Integer.parseInt(subArgsArr[1]));
                    }
                }
                if (arg.startsWith("--log=")) {
                    override = true;
                    String subArgs = arg.replace("--log=", "");
                    String[] subArgsArr = subArgs.split(",");
                    for (int i1 = 0, max1 = subArgsArr.length; i1 < max1; i1++) {
                        if ((i1 + 1) < max1) {
                            String _module = subArgsArr[i1].toUpperCase();
                            String _level = subArgsArr[++i1].toUpperCase();
                            this.log.getModules().put(_module, _level);
                        }
                    }
                }
            }
            if (override) System.out.println("Config Override");
        }

        XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter sw = null;

        try {

            sw = output.createXMLStreamWriter(new FileWriter(file));
            sw.writeStartDocument("utf-8", "1.0");
            sw.writeCharacters("\r\n");
            sw.writeStartElement("aion");

            sw.writeCharacters("\r\n\t");
            sw.writeStartElement("mode");
            sw.writeCharacters(this.getMode());
            sw.writeEndElement();

            sw.writeCharacters("\r\n\t");
            sw.writeStartElement("id");
            sw.writeCharacters(this.getId());
            sw.writeEndElement();

            if (keystorePath != null) {
                sw.writeCharacters("\r\n\t");
                sw.writeStartElement("keystore");
                sw.writeCharacters(keystorePath);
                sw.writeEndElement();
            }

            sw.writeCharacters(this.getApi().toXML());
            sw.writeCharacters(this.getNet().toXML());
            sw.writeCharacters(this.getSync().toXML());
            sw.writeCharacters(this.getConsensus().toXML());
            sw.writeCharacters(this.getDb().toXML());
            sw.writeCharacters(this.getLog().toXML());
            sw.writeCharacters(this.getTx().toXML());
            sw.writeCharacters(this.getReports().toXML());
            sw.writeCharacters(this.getGui().toXML());

            sw.writeCharacters("\r\n");
            sw.writeEndElement();
            sw.flush();
            sw.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("<error on-write-config-xml-to-file>");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        } finally {
            if (sw != null) {
                try {
                    sw.close();
                } catch (XMLStreamException e) {
                    System.out.println("<error on-close-stream-writer>");
                    System.exit(SystemExitCodes.INITIALIZATION_ERROR);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgAion cfgAion = (CfgAion) o;
        return Objects.equal(genesis, cfgAion.genesis);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(genesis);
    }
}
