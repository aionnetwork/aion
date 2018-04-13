package org.aion.zero.impl;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryConfig;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.zero.impl.db.ContractDetailsAion;

public class MockRepositoryConfig implements IRepositoryConfig {
    @Override
    public String[] getVendorList() {
        return new String[] { DBVendor.MOCKDB.toValue() };
    }

    @Override
    public String getActiveVendor() {
        return DBVendor.MOCKDB.toValue();
    }

    @Override
    public String getDbPath() {
        return "";
    }

    @Override
    public int getPrune() {
        return 0;
    }

    @Override
    public IContractDetails contractDetailsImpl() {
        return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
    }

    @Override
    public boolean isAutoCommitEnabled() {
        return false;
    }

    @Override
    public boolean isDbCacheEnabled() {
        return false;
    }

    @Override
    public boolean isDbCompressionEnabled() {
        return false;
    }

    @Override
    public boolean isHeapCacheEnabled() {
        return true;
    }

    @Override
    public String getMaxHeapCacheSize() {
        return "0";
    }

    @Override
    public boolean isHeapCacheStatsEnabled() {
        return false;
    }

    @Override
    public int getMaxFdAllocSize() {
        return LevelDBConstants.MAX_OPEN_FILES;
    }

    // default levelDB setting, may want to change this later
    @Override
    public int getBlockSize() {
        return LevelDBConstants.BLOCK_SIZE;
    }

    @Override
    public int getWriteBufferSize() {
        return LevelDBConstants.WRITE_BUFFER_SIZE;
    }

    @Override
    public int getCacheSize() {
        return LevelDBConstants.CACHE_SIZE;
    }
}
