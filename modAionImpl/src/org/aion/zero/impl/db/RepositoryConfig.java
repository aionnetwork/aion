/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.db;

import org.aion.base.db.DetailsProvider;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryConfig;

public class RepositoryConfig implements IRepositoryConfig {

    private final String[] vendorList;
    private final String activeVendor;
    private final String dbPath;
    private final int prune;
    private final DetailsProvider detailsProvider;

    private boolean enable_auto_commit;
    private boolean enable_db_cache;
    private boolean enable_db_compression;

    private boolean enable_heap_cache;
    private String max_heap_cache_size;
    private boolean enable_heap_cache_stats;

    private final int block_size;
    private final int max_fd_alloc_size;

    private final int write_buffer_size;
    private final int cache_size;

    @Override
    public String[] getVendorList() {
        return vendorList;
    }

    @Override
    public String getActiveVendor() {
        return activeVendor;
    }

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @Override
    public int getPrune() {
        return prune;
    }

    @Override
    public IContractDetails contractDetailsImpl() {
        return this.detailsProvider.getDetails();
    }

    @Override
    public boolean isAutoCommitEnabled() {
        return enable_auto_commit;
    }

    @Override
    public boolean isDbCacheEnabled() {
        return enable_db_cache;
    }

    @Override
    public boolean isDbCompressionEnabled() {
        return enable_db_compression;
    }

    @Override
    public boolean isHeapCacheEnabled() {
        return enable_heap_cache;
    }

    @Override
    public String getMaxHeapCacheSize() {
        return max_heap_cache_size;
    }

    @Override
    public boolean isHeapCacheStatsEnabled() {
        return enable_heap_cache_stats;
    }

    @Override
    public int getMaxFdAllocSize() {
        return this.max_fd_alloc_size;
    }

    @Override
    public int getBlockSize() {
        return this.block_size;
    }

    @Override
    public int getWriteBufferSize() {
        return this.write_buffer_size;
    }

    @Override
    public int getCacheSize() {
        return this.cache_size;
    }

    public RepositoryConfig(final String[] vendorList, //
                            final String activeVendor, //
                            final String dbPath, //
                            final int prune, //
                            final DetailsProvider detailsProvider, //
                            final boolean enable_auto_commit, //
                            final boolean enable_db_cache, //
                            final boolean enable_db_compression, //
                            final boolean enable_heap_cache, //
                            final String max_heap_cache_size, //
                            final boolean enable_heap_cache_stats,
                            final int max_fd_alloc_size,
                            final int block_size,
                            final int write_buffer_size,
                            final int cache_size) { //

        this.vendorList = vendorList;
        this.activeVendor = activeVendor;
        this.dbPath = dbPath;
        this.prune = prune;
        this.detailsProvider = detailsProvider;

        // parameters for describing database functionality
        this.enable_auto_commit = enable_auto_commit;
        this.enable_db_cache = enable_db_cache;
        this.enable_db_compression = enable_db_compression;

        // parameters for describing cache functionality
        this.enable_heap_cache = enable_heap_cache;
        this.max_heap_cache_size = max_heap_cache_size;
        this.enable_heap_cache_stats = enable_heap_cache_stats;

        this.max_fd_alloc_size = max_fd_alloc_size;
        this.block_size = block_size;

        this.write_buffer_size = write_buffer_size;
        this.cache_size = cache_size;
    }

}
