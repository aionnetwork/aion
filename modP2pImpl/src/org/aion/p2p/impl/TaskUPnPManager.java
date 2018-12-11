package org.aion.p2p.impl;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import fr.free.miniupnp.IGDdatas;
import fr.free.miniupnp.MiniupnpcLibrary;
import fr.free.miniupnp.UPNPDev;
import fr.free.miniupnp.UPNPUrls;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class TaskUPnPManager implements Runnable {

    private static final String UPNP_PROTOCOL_TCP = "TCP";
    private static final String UPNP_PORT_MAPPING_DESCRIPTION = "aion-upnp";
    private static final int DEFAULT_UPNP_PORT_MAPPING_LIFETIME_IN_SECONDS = 3600;
    private static final int UPNP_DELAY = 2000;

    private int port;
    private MiniupnpcLibrary miniupnpc;

    public TaskUPnPManager(int port) {
        this.port = port;
        miniupnpc = MiniupnpcLibrary.INSTANCE;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-upnp");

        UPNPUrls urls = new UPNPUrls();
        IGDdatas data = new IGDdatas();

        ByteBuffer lanaddr = ByteBuffer.allocate(16);

        UPNPDev devlist =
                miniupnpc.upnpDiscover(
                        UPNP_DELAY, null, null, 0, 0, (byte) 2, IntBuffer.allocate(1));
        if (devlist != null) {
            if (miniupnpc.UPNP_GetValidIGD(devlist, urls, data, lanaddr, 16) != 0) {

                if (p2pLOG.isInfoEnabled()) {
                    p2pLOG.info("<p2p-upnp found-possible-igd=" + urls.controlURL.getString(0) + ">" +
                        "\n<p2p-upnp local-lan-ip=" + new String(lanaddr.array()) + ">");
                }

                getExternalIpAddress(urls, data);
                addPortMapping(urls, data, lanaddr);
                getMappedPortInfo(urls, data);

                miniupnpc.FreeUPNPUrls(urls);
            } else {
                if (p2pLOG.isInfoEnabled()) {
                    p2pLOG.info("<p2p-upnp no-valid-upnp-internet-gateway-device-found>");
                }
            }
        } else {
            if (p2pLOG.isInfoEnabled()) {
                p2pLOG.info("<p2p-upnp no-igd-upnp-device-found-on-network>");
            }
        }
    }

    private void addPortMapping(UPNPUrls urls, IGDdatas data, ByteBuffer lanaddr) {
        int ret =
                miniupnpc.UPNP_AddPortMapping(
                        urls.controlURL.getString(0),
                        new String(data.first.servicetype),
                        String.valueOf(port),
                        String.valueOf(port),
                        new String(lanaddr.array()),
                        UPNP_PORT_MAPPING_DESCRIPTION,
                        UPNP_PROTOCOL_TCP,
                        null,
                        String.valueOf(DEFAULT_UPNP_PORT_MAPPING_LIFETIME_IN_SECONDS));

        if (ret != MiniupnpcLibrary.UPNPCOMMAND_SUCCESS)
            if (p2pLOG.isInfoEnabled()) {
                p2pLOG.info("<p2p-upnp add-port-mapping-failed code=" + ret + ">");
            }
    }

    private void getMappedPortInfo(UPNPUrls urls, IGDdatas data) {
        ByteBuffer intClient = ByteBuffer.allocate(16);
        ByteBuffer intPort = ByteBuffer.allocate(6);
        ByteBuffer desc = ByteBuffer.allocate(80);
        ByteBuffer enabled = ByteBuffer.allocate(4);
        ByteBuffer leaseDuration = ByteBuffer.allocate(16);

        int ret =
                miniupnpc.UPNP_GetSpecificPortMappingEntry(
                        urls.controlURL.getString(0),
                        new String(data.first.servicetype),
                        String.valueOf(port),
                        UPNP_PROTOCOL_TCP,
                        null,
                        intClient,
                        intPort,
                        desc,
                        enabled,
                        leaseDuration);

        if (ret != MiniupnpcLibrary.UPNPCOMMAND_SUCCESS) {

            if (p2pLOG.isInfoEnabled()) {
                p2pLOG.info("<p2p-upnp get-specific-port-mapping-entry-failed code=" + ret + ">");
            }

            return;
        }

        if (p2pLOG.isInfoEnabled()) {
            p2pLOG.info("<p2p-upnp internal-ip-port="
                + new String(intClient.array())
                + ":"
                + new String(intPort.array())
                + "("
                + new String(desc.array())
                + ")>");
        }
    }

    private void getExternalIpAddress(UPNPUrls urls, IGDdatas data) {
        ByteBuffer externalAddress = ByteBuffer.allocate(16);
        int ret =
                miniupnpc.UPNP_GetExternalIPAddress(
                        urls.controlURL.getString(0),
                        new String(data.first.servicetype),
                        externalAddress);

        if (ret != MiniupnpcLibrary.UPNPCOMMAND_SUCCESS) {
            if (p2pLOG.isInfoEnabled()) {
                p2pLOG.info("<p2p-upnp get-external-ip-command-failed code=" + ret + ">");
            }
            return;
        }

        if (p2pLOG.isInfoEnabled()) {
            p2pLOG.info("<p2p-upnp external-ip=" + new String(externalAddress.array()) + ">");
        }
    }
}
