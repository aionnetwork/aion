/*
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
 */

package org.aion.base.db;

/**
 * Interface for pruning configuration parameters.
 *
 * @author Alexandra Roatis
 */
public interface IPruneConfig {

    /**
     * Indicates if pruning should be enabled or disabled.
     *
     * @return {@code true} when pruning enabled, {@code false} when pruning disabled.
     */
    boolean isEnabled();

    /**
     * Indicates if archiving should be enabled or disabled.
     *
     * @return {@code true} when archiving enabled, {@code false} when archiving disabled.
     */
    boolean isArchived();

    /**
     * @return the number of topmost blocks for which the full data should be maintained on disk.
     */
    int getCurrentCount();

    /**
     * Gets the rate at which blocks should be archived (for which the full data should be
     * maintained on disk). Blocks that are exact multiples of the returned value should be
     * persisted on disk, regardless of other pruning.
     *
     * @return integer value representing the archive rate
     */
    int getArchiveRate();
}
