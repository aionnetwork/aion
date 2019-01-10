package org.aion.mcf.config;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** @author chris lin, ali sharif */
public final class CfgApiRpc {

    CfgApiRpc() {
        this.active = true;
        this.ip = "127.0.0.1";
        this.port = 8545;
        this.corsEnabled = false;
        this.corsOrigin = "*";
        this.filtersEnabled = true;
        // using a strings here for the following 2 properties instead of referencing the associated
        // enum value
        // since don't want to add dependency to modApiServer just for this
        this.vendor = "undertow";
        this.enabled = new ArrayList<>(Arrays.asList("web3", "eth", "personal", "stratum", "ops"));

        // nulls for the following properties indicates to consumer of these properties
        // to "choose reasonable defaults"
        this.workerThreads = null;
        this.ioThreads = null;
        this.requestQueueSize = null; // null = unbounded queue size
        this.stuckThreadDetectorEnabled = true;
        this.enabledMethods = null;
        this.disabledMethods = null;

        this.ssl = new CfgSsl();
    }

    private boolean active;
    private String ip;
    private int port;
    private List<String> enabled;
    private List<String> enabledMethods;
    private List<String> disabledMethods;
    private boolean corsEnabled;
    private String corsOrigin;
    private boolean filtersEnabled;
    private CfgSsl ssl;
    private String vendor;

    private Integer workerThreads;
    private Integer ioThreads;
    private Integer requestQueueSize;
    private boolean stuckThreadDetectorEnabled;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        // get the attributes
        this.active = Boolean.parseBoolean(sr.getAttributeValue(null, "active"));
        this.ip = sr.getAttributeValue(null, "ip");
        this.port = Integer.parseInt(sr.getAttributeValue(null, "port"));

        // get the nested elements
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "cors-enabled":
                            try {
                                corsEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println(
                                        "failed to read config node: aion.api.rpc.cors-enabled; using preset: "
                                                + corsEnabled);
                                // e.printStackTrace();
                            }
                            break;
                        case "cors-origin":
                            try {
                                corsOrigin = Cfg.readValue(sr).trim();
                            } catch (Exception e) {
                                System.out.println(
                                        "failed to read config node: aion.api.rpc.cors-origin; using preset: "
                                                + corsOrigin);
                                // e.printStackTrace();
                            }
                            break;
                        case "apis-enabled":
                            String cs = Cfg.readValue(sr).trim();
                            this.enabled =
                                    new ArrayList<>(
                                            Stream.of(cs.split(","))
                                                    .map(String::trim)
                                                    .filter(s -> !s.isEmpty())
                                                    .collect(Collectors.toList()));
                            break;
                        case "api-methods-enabled":
                            String enabledMethods = Cfg.readValue(sr).trim();
                            this.enabledMethods =
                                    new ArrayList<>(
                                            Stream.of(enabledMethods.split(","))
                                                    .map(String::trim)
                                                    .filter(s -> !s.isEmpty())
                                                    .collect(Collectors.toList()));
                            break;
                        case "api-methods-disabled":
                            String disabledMethods = Cfg.readValue(sr).trim();
                            this.disabledMethods =
                                    new ArrayList<>(
                                            Stream.of(disabledMethods.split(","))
                                                    .map(String::trim)
                                                    .filter(s -> !s.isEmpty())
                                                    .collect(Collectors.toList()));
                            break;
                        case "vendor":
                            try {
                                this.vendor = Cfg.readValue(sr).trim();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case "worker-threads":
                            {
                                try {
                                    int t = Integer.parseInt(Cfg.readValue(sr));
                                    // filter out negative counts
                                    if (t > 0) this.workerThreads = t;
                                    // otherwise, accept default set in constructor
                                } catch (Exception e) {
                                    System.out.println(
                                            "Illegal value for aion.api.rpc.worker-threads; will select reasonable defaults.");
                                    e.printStackTrace();
                                }

                                break;
                            }
                        case "io-threads":
                            {
                                try {
                                    int t = Integer.parseInt(Cfg.readValue(sr));
                                    // filter out negative counts
                                    if (t > 0) this.ioThreads = t;
                                    // otherwise, accept default set in constructor
                                } catch (Exception e) {
                                    System.out.println(
                                            "Illegal value for aion.api.rpc.io-threads; will select reasonable defaults.");
                                    e.printStackTrace();
                                }

                                break;
                            }
                        case "request-queue-size":
                            {
                                try {
                                    int t = Integer.parseInt(Cfg.readValue(sr));
                                    // filter out negative counts
                                    if (t > 0) this.requestQueueSize = t;
                                    // otherwise, accept default set in constructor
                                } catch (Exception e) {
                                    System.out.println(
                                            "Illegal value for aion.api.rpc.request-queue-size; will select reasonable defaults.");
                                    e.printStackTrace();
                                }

                                break;
                            }
                        case "stuck-thread-detector-enabled":
                            {
                                try {
                                    stuckThreadDetectorEnabled =
                                            Boolean.parseBoolean(Cfg.readValue(sr));
                                } catch (Exception e) {
                                    System.out.println(
                                            "failed to read config node: aion.api.rpc.stuckThreadDetectorEnabled; using preset: "
                                                    + stuckThreadDetectorEnabled);
                                    e.printStackTrace();
                                }
                                break;
                            }
                        case "filters-enabled":
                            {
                                try {
                                    filtersEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                                } catch (Exception e) {
                                    System.out.println(
                                            "failed to read config node: aion.api.rpc.filters-enabled; using preset: "
                                                    + filtersEnabled);
                                    e.printStackTrace();
                                }
                                break;
                            }
                        case "ssl":
                            this.ssl.fromXML(sr);
                            break;
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
            }
        }

        sr.next();
    }

    String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            // <rpc active="false" ip="127.0.0.1" port="8545"/>

            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "rpc config docs: https://github.com/aionnetwork/aion/wiki/JSON-RPC-API-Docs");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("rpc");

            xmlWriter.writeAttribute("active", this.active ? "true" : "false");
            xmlWriter.writeAttribute("ip", this.ip);
            xmlWriter.writeAttribute("port", this.port + "");

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("cors-enabled");
            xmlWriter.writeCharacters(String.valueOf(this.isCorsEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment(
                    "comma-separated list, APIs available: web3,net,debug,personal,eth,stratum");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("apis-enabled");
            xmlWriter.writeCharacters(String.join(",", this.getEnabled()));
            xmlWriter.writeEndElement();

            if (this.getEnabledMethods() != null) {
                xmlWriter.writeStartElement("api-methods-enabled");
                xmlWriter.writeCharacters(String.join(",", this.getEnabledMethods()));
                xmlWriter.writeEndElement();
            }

            if (this.getDisabledMethods() != null) {
                xmlWriter.writeStartElement("api-methods-disabled");
                xmlWriter.writeCharacters(String.join(",", this.getDisabledMethods()));
                xmlWriter.writeEndElement();
            }

            // don't write-back ssl. (keep it hidden for now)
            // xmlWriter.writeCharacters(this.ssl.toXML());

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeEndElement();
            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public String getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public String getCorsOrigin() {
        return corsOrigin;
    }

    public List<String> getEnabled() {
        return enabled;
    }

    public List<String> getEnabledMethods() {
        return enabledMethods;
    }

    public List<String> getDisabledMethods() {
        return disabledMethods;
    }

    public boolean isFiltersEnabled() {
        return filtersEnabled;
    }

    public CfgSsl getSsl() {
        return this.ssl;
    }

    public String getVendor() {
        return vendor;
    }

    public Integer getWorkerThreads() {
        return workerThreads;
    }

    public Integer getIoThreads() {
        return ioThreads;
    }

    public Integer getRequestQueueSize() {
        return requestQueueSize;
    }

    public boolean isStuckThreadDetectorEnabled() {
        return stuckThreadDetectorEnabled;
    }

    /**
     * @implNote this should theoretically work, but should be tested for correctness by any future
     *     consumer
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgApiRpc cfg = (CfgApiRpc) o;

        return active == cfg.active
                && Objects.equals(ip, cfg.ip)
                && port == cfg.port
                && Objects.equals(enabled, cfg.enabled)
                && Objects.equals(enabledMethods, cfg.enabledMethods)
                && Objects.equals(disabledMethods, cfg.disabledMethods)
                && corsEnabled == cfg.corsEnabled
                && Objects.equals(corsOrigin, cfg.corsOrigin)
                && filtersEnabled == cfg.filtersEnabled
                && Objects.equals(ssl, cfg.ssl)
                && Objects.equals(vendor, cfg.vendor)
                && Objects.equals(workerThreads, cfg.workerThreads)
                && Objects.equals(ioThreads, cfg.ioThreads)
                && Objects.equals(requestQueueSize, cfg.requestQueueSize)
                && stuckThreadDetectorEnabled == cfg.stuckThreadDetectorEnabled;
    }

    /**
     * @implNote this should theoretically work, but should be tested for correctness by any future
     *     consumer
     * @implNote computationally slowest implementation O(n). there are faster ways of doing this if
     *     this function call ends up on a critical path (probably not)
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                active,
                ip,
                port,
                enabled,
                enabledMethods,
                disabledMethods,
                corsEnabled,
                corsOrigin,
                filtersEnabled,
                ssl,
                vendor,
                workerThreads,
                ioThreads,
                requestQueueSize,
                stuckThreadDetectorEnabled);
    }
}
