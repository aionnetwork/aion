package org.aion.base.db;

/**
 * Interface for a details provider, provides instances of contract details
 *
 * @author yao
 */
public interface DetailsProvider {
    IContractDetails getDetails();
}
