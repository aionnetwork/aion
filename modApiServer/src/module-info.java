module aion.apiserver {
    requires aion.zero.impl;
    requires aion.log;
    requires aion.p2p;
    requires aion.mcf;
    requires aion.crypto;
    requires slf4j.api;
    requires aion.evtmgr;
    requires aion.evtmgr.impl;
    requires aion.fastvm;
    requires org.json;
    requires commons.collections4;
    requires nanohttpd;
    requires undertow.core;
    requires protobuf.java;
    requires commons.lang3;
    requires com.github.benmanes.caffeine;
    requires com.google.common;
    requires jdk.unsupported;
    requires aion.util;
    requires libnzmq;
    requires aion.types;
    requires aion.base;
    //requires jackson.annotations;
    //requires jackson.core;
    //requires jackson.databind;
    requires AionRpc;

    exports org.aion.api.server.pb;
    exports org.aion.api.server.zmq;
    exports org.aion.api.server.http;
    exports org.aion.api.server.http.nano;
    exports org.aion.api.server.http.undertow;
}
