package org.aion.zero.impl.db;

import org.aion.mcf.db.ContractDetails;

/**
 * Interface for contract details that are stored in the database.
 *
 * @author Alexandra Roatis
 */
public interface StoredContractDetails extends ContractDetails {

    /**
     * Returns a byte array representation of the contract details to be stored in a database.
     *
     * @return a byte array representation of the contract details to be stored in a database
     */
    byte[] getEncoded();

    /**
     * Commits the contract updates that change the storage hash to disc. This includes updating the
     * external storage trie and the object graph (when applicable).
     */
    void syncStorage();
}
