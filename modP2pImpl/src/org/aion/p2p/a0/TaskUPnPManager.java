package org.aion.p2p.a0;

import fr.free.miniupnp.IGDdatas;
import fr.free.miniupnp.MiniupnpcLibrary;
import fr.free.miniupnp.UPNPDev;
import fr.free.miniupnp.UPNPUrls;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class TaskUPnPManager implements Runnable {

    private final static String UPNP_PROTOCOL_TCP = "TCP";
    private final static String UPNP_PORT_MAPPING_DESCRIPTION = "aion-UPnP";
    private final static int DEFAULT_UPNP_PORT_MAPPING_LIFETIME_IN_SECONDS = 3600;
    private final static int UPNP_DELAY = 2000;

    private int port;
    MiniupnpcLibrary miniupnpc;

    TaskUPnPManager(int port){
        this.port = port;
        miniupnpc = MiniupnpcLibrary.INSTANCE;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-UPnP");

        UPNPUrls urls = new UPNPUrls();
        IGDdatas data = new IGDdatas();

        ByteBuffer lanaddr = ByteBuffer.allocate(16);

        UPNPDev devlist = miniupnpc.upnpDiscover(UPNP_DELAY,  null,  null, 0, 0, (byte) 2, IntBuffer.allocate(1));
        if (devlist != null) {
            if (miniupnpc.UPNP_GetValidIGD(devlist, urls, data, lanaddr, 16) != 0) {
                System.out.println("<p2p-UPnP Found possible IGD : " + urls.controlURL.getString(0) + ">");
                System.out.println("<p2p-UPnP Local LAN ip address : " + new String(lanaddr.array()) + ">");

                getExternalIpAddress(urls, data);
                addPortMapping(urls, data, lanaddr);
                getMappedPortInfo(urls, data);

                miniupnpc.FreeUPNPUrls(urls);
            } else {
                System.out.println("<p2p-UPnP no-valid-UPNP-internet-gateway-device-found>");
            }
        } else {
            System.out.println("<p2p-UPnP no-IGD-UPnP-device-found-on-network>");
        }
    }

    private void addPortMapping(UPNPUrls urls, IGDdatas data, ByteBuffer lanaddr) {
        int ret = miniupnpc.UPNP_AddPortMapping(
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
            System.out.println("<p2p-UPnP AddPortMapping() failed with code " + ret + ">");
    }

    private void getMappedPortInfo(UPNPUrls urls, IGDdatas data) {
        ByteBuffer intClient = ByteBuffer.allocate(16);
        ByteBuffer intPort = ByteBuffer.allocate(6);
        ByteBuffer desc = ByteBuffer.allocate(80);
        ByteBuffer enabled = ByteBuffer.allocate(4);
        ByteBuffer leaseDuration = ByteBuffer.allocate(16);

        int ret = miniupnpc.UPNP_GetSpecificPortMappingEntry(
                urls.controlURL.getString(0), new String(data.first.servicetype),
                String.valueOf(port), UPNP_PROTOCOL_TCP, null, intClient, intPort,
                desc, enabled, leaseDuration);

        if (ret != MiniupnpcLibrary.UPNPCOMMAND_SUCCESS) {
            System.out.println("<p2p-UPnP GetSpecificPortMappingEntry() failed with code " + ret + ">");
            return;
        }

        System.out.println("<p2p-UPnP InternalIP:Port = " +
                new String(intClient.array()) + ":" + new String(intPort.array()) +
                " (" + new String(desc.array()) + ") >");
    }

    private void getExternalIpAddress(UPNPUrls urls, IGDdatas data) {
        ByteBuffer externalAddress = ByteBuffer.allocate(16);
        int ret = miniupnpc.UPNP_GetExternalIPAddress(urls.controlURL.getString(0),
                new String(data.first.servicetype), externalAddress);

        if(ret != MiniupnpcLibrary.UPNPCOMMAND_SUCCESS) {
            System.out.println("<p2p-UPnP get external ip address command failed with code = " + ret + ">");
            return;
        }

        System.out.println("<p2p-UPnP ExternalIPAddress = " + new String(externalAddress.array()) + ">");
    }
}
