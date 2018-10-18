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

package org.aion.gui.model.dto;

import org.aion.gui.model.AbstractAionApiClient;
import org.aion.gui.model.ApiDataRetrievalException;
import org.aion.gui.model.IApiMsgErrorHandler;
import org.aion.gui.model.KernelConnection;

public abstract class AbstractDto extends AbstractAionApiClient {

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    protected AbstractDto(KernelConnection kernelConnection, IApiMsgErrorHandler errorHandler) {
        super(kernelConnection, errorHandler);
    }

    /**
     * Populate this DTO by calling the Aion API.
     *
     * @throws ApiDataRetrievalException if data could not be retrieved
     * @returns null
     */
    public Void loadFromApi() throws ApiDataRetrievalException {
        loadFromApiInternal();
        return null;
    }

    abstract protected void loadFromApiInternal();
}
