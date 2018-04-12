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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.db.impl;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.h2.store.fs.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Common functionality for database implementations.
 *
 * @author Alexandra Roatis
 * @implNote Assumes persistent database. Overwrite method if this is not the case.
 */
public abstract class AbstractDB implements IByteArrayKeyValueDatabase {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    protected static final int DEFAULT_CACHE_SIZE_BYTES = 128 * 1024 * 1024; // 128mb
    protected static final int DEFAULT_WRITE_BUFFER_SIZE_BYTES = 16 * 1024 * 1024; // 16mb

    protected final String name;
    protected String path = null;
    protected boolean enableDbCache = false;
    protected boolean enableDbCompression = false;

    protected AbstractDB(String name) {
        Objects.requireNonNull(name, "The database name cannot be null.");
        this.name = name;
    }

    protected AbstractDB(String name, String path, boolean enableDbCache, boolean enableDbCompression) {
        this(name);

        Objects.requireNonNull(path, "The database path cannot be null.");
        this.path = new File(path, name).getAbsolutePath();

        this.enableDbCache = enableDbCache;
        this.enableDbCompression = enableDbCompression;
    }

    protected String propertiesInfo() {
        return "<name=" + name + ",autocommit=ON,cache=" + (enableDbCache ? "ON" : "OFF") + //
                ",compression=" + (enableDbCompression ? "ON" : "OFF") + ">"; //
    }

    @Override
    public boolean commit() {
        // not implemented since we always commit the changes to the database for this implementation
        throw new UnsupportedOperationException("Only automatic commits are supported by " + this.toString());
    }

    @Override
    public void compact() {
        LOG.warn("Compact not supported by " + this.toString() + ".");
    }

    @Override
    public void drop() {
        close();

        try (Stream<Path> stream = Files.walk(new File(path).toPath())) {
            stream.map(Path::toFile).forEach(File::delete);
        } catch (Exception e) {
            LOG.error("Unable to delete path due to: ", e);
        }

        open();
    }

    @Override
    public Optional<String> getName() {
        return Optional.ofNullable(this.name);
    }

    @Override
    public Optional<String> getPath() {
        return Optional.ofNullable(this.path);
    }

    /**
     * Checks that the database connection is open.
     * Throws a {@link RuntimeException} if the database connection is closed.
     *
     * @implNote Always do this check after acquiring a lock on the class/data.
     *         Otherwise it might produce inconsistent results due to lack of synchronization.
     */
    protected void check() {
        if (!isOpen()) {
            throw new RuntimeException("Database is not opened: " + this);
        }
    }

    /**
     * Checks that the given key is not null.
     * Throws a {@link IllegalArgumentException} if the key is null.
     */
    public static void check(byte[] k) {
        if (k == null) {
            throw new IllegalArgumentException("The database does not accept null keys.");
        }
    }

    /**
     * Checks that the given collection of keys does not contain null values.
     * Throws a {@link IllegalArgumentException} if a null key is present.
     */
    public static void check(Collection<byte[]> keys) {
        if (keys.contains(null)) {
            throw new IllegalArgumentException("The database does not accept null keys.");
        }
    }

    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean isAutoCommitEnabled() {
        // autocommit is always enabled when not overwritten by the class
        return true;
    }

    @Override
    public boolean isPersistent() {
        // always persistent when not overwritten by the class
        return true;
    }

    /**
     * For testing the lock functionality of public methods.
     * Helps ensure that locks are released after normal or exceptional execution.
     *
     * @return {@code true} when the resource is locked,
     *         {@code false} otherwise
     */
    @Override
    public boolean isLocked() {
        return false;
    }

    /**
     * Functionality for directly interacting with the heap cache.
     */
    public abstract boolean commitCache(Map<ByteArrayWrapper, byte[]> cache);

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    @Override
    public Optional<byte[]> get(byte[] k) {
        check(k);

        check();

        byte[] v = getInternal(k);

        return Optional.ofNullable(v);
    }

    /**
     * Database specific get functionality, without locking required. Locking is applied in {@link #get(byte[])}.
     *
     * @param k
     *         the key for which the method must return the associated value
     * @return the value stored in the database for the give key.
     */
    protected abstract byte[] getInternal(byte[] k);

}