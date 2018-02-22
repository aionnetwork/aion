module aion.crypto {
    requires slf4j.api;
    requires aion.base;
    requires aion.rlp;
    exports org.aion.crypto;
    exports org.aion.crypto.hash;
    exports org.aion.crypto.ed25519;
}
