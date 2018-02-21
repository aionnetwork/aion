//package org.aion.p2p.a0;
//
//import org.junit.Test;
//
//import java.util.Arrays;
//import java.util.Map;
//
//import static org.junit.Assert.assertTrue;
//
//public class P2pMgrTest {
//
//    @Test
//    public void testIgnoreBothIpAndPortSameFromAddingToBootList() {
//        String _nodeId1 = "8d63cba9-0b21-4024-953f-7285073ac313";
//        String _nodeId2 = "caed7fa5-d2a9-4f59-99d2-289172562863";
//        String _ip = "127.0.0.1";
//        int _port = 30303;
//        String[] _nodes = new String[2];
//        _nodes[0] = "p2p://" + _nodeId2 + "@" + _ip + ":" + _port;
//        _nodes[1] = "p2p://" + _nodeId2 + "@192.168.0.100:" + _port;
//        P2pMgr p2p = new P2pMgr(_nodeId1, _ip, _port, _nodes, false, false, false);
//
////        // expecting _node[0] should not be added
////        Map<Integer, Node> tempNodes = p2p.getTempNodes();
////        assertTrue(tempNodes.size() == 1);
////        Map.Entry<Integer, Node> entry = tempNodes.entrySet().iterator().next();
////        assertTrue(entry.getValue().getPort() == _port);
////        assertTrue(Arrays.equals(entry.getValue().getIp(), Helper.ipStrToBytes("192.168.0.100")));
//    }
//
//}
