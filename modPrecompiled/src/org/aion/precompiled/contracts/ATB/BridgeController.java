package org.aion.precompiled.contracts.ATB;

/**
 * Contains the functional components of the Aion Token Bridge, this class is removed
 * from concerns regarding communicate with outside world (external) and communicating
 * with the database. See {@link BridgeSerializationConnector} and {@link BridgeStorageConnector}
 * respectively for information on those layers of components
 */
public class BridgeController {

    private BridgeStorageConnector storageConnector;


    public BridgeController(final BridgeStorageConnector storageConnector) {
        this.storageConnector = storageConnector;
    }

    /**
     * Loads in the stored state from the underlying repository
     */
    public void load() {

    }
}
