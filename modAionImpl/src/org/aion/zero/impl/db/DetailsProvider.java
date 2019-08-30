package org.aion.zero.impl.db;

import org.aion.mcf.db.ContractDetails;

/**
 * Interface for a details provider, provides instances of contract details
 *
 * @author yao
 */
public interface DetailsProvider {
    ContractDetails getDetails();
}
