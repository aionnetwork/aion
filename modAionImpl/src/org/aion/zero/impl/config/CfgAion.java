/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.zero.impl.config;

import java.io.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.xml.stream.*;
import org.aion.mcf.config.*;
import org.aion.zero.exceptions.HeaderStructureException;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.GenesisBlockLoader;

/**
 * @author chris
 */
public final class CfgAion extends Cfg {

    protected AionGenesis genesis;

    protected static final int N = 210;

    private static final int K = 9;

    private static final String NODE_ID_PLACEHOLDER = "[NODE-ID-PLACEHOLDER]";

    private CfgAion() {
        this.mode = "aion";
        this.id = UUID.randomUUID().toString();
        this.net = new CfgNet();
        this.consensus = new CfgConsensusPow();
        this.sync = new CfgSync();
        this.api = new CfgApi();
        this.db = new CfgDb();
        this.log = new CfgLog();
        this.tx = new CfgTx();
        this.reports = new CfgReports();
        this.gui = new CfgGui();
    }

    private static class CfgAionHolder {
        private static final CfgAion inst = new CfgAion();
    }

    public static CfgAion inst() {
        return CfgAionHolder.inst;
    }

    @Override
    public void setGenesis() {
        try {
            this.genesis = GenesisBlockLoader.loadJSON(GENESIS_FILE_PATH);
        } catch (IOException | HeaderStructureException e) {
            System.out.println(String.format("Genesis load exception %s", e.getMessage()));
            System.out.println("defaulting to default AionGenesis configuration");
            try {
                this.genesis = (new AionGenesis.Builder()).build();
            } catch (HeaderStructureException e2) {
                // if this fails, it means our DEFAULT genesis violates header rules
                // this is catastrophic
                throw new RuntimeException(e2);
            }
        }
    }

    public CfgConsensusPow getConsensus() {
        return (CfgConsensusPow) this.consensus;
    }

    public synchronized AionGenesis getGenesis() {
        if (this.genesis == null)
            setGenesis();
        return this.genesis;
    }

    public static int getN() {
        return N;
    }

    public static int getK() {
        return K;
    }

    private void closeFileInputStream(final FileInputStream fis){
        if (fis != null) {
            try {
                fis.close();
            } catch (IOException e) {
                System.out.println("<error on-close-file-input-stream>");
                System.exit(1);
            }
        }
    }

    public void dbFromXML() {
        File cfgFile = new File(CONF_FILE_PATH);
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            loop: while (sr.hasNext()) {
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
                    if (sr.getLocalName().toLowerCase().equals("aion"))
                        break loop;
                    else
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(1);
        } finally {
            closeFileInputStream(fis);
        }
    }

    @Override
    public boolean fromXML() {
        boolean shouldWriteBackToFile = false;
        File cfgFile = new File(CONF_FILE_PATH);
        if(!cfgFile.exists())
            return false;
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            loop: while (sr.hasNext()) {
                int eventType = sr.next();
                switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                    case "id":
                        String nodeId = readValue(sr);
                        if(NODE_ID_PLACEHOLDER.equals(nodeId)) {
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
                    if (sr.getLocalName().toLowerCase().equals("aion"))
                        break loop;
                    else
                        break;
                }
            }
            closeFileInputStream(fis);
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(1);
        }
        return shouldWriteBackToFile;
    }

    @Override
    public void toXML(final String[] args) {
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
                            if (!subArg.equals(""))
                                _nodes.add(subArg);
                        }
                        this.getNet().setNodes(_nodes.toArray(new String[0]));
                    }
                }
                if (arg.startsWith("--p2p=")) {
                    override = true;
                    String[] subArgsArr = arg.replace("--p2p=", "").split(",");
                    if (subArgsArr.length == 2) {
                        this.getNet().getP2p().setIp(subArgsArr[0]);
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
            if (override)
                System.out.println("Config Override");
        }

        XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter sw = null;

        try {

            sw = output.createXMLStreamWriter(new FileWriter(CONF_FILE_PATH));
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
            System.exit(1);
        } finally {
            if (sw != null) {
                try {
                    sw.close();
                } catch (XMLStreamException e) {
                    System.out.println("<error on-close-stream-writer>");
                    System.exit(1);
                }
            }
        }
    }
}
