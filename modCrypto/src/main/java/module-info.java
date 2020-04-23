module aion.crypto {
    requires slf4j.api;
    requires aion.util;
    requires core;
    requires prov;

    exports org.aion.crypto;
    exports org.aion.crypto.hash;
    exports org.aion.crypto.ed25519;
    exports org.aion.crypto.vrf;
    exports org.libsodium.jni; // revisit
}
