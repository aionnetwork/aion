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

package org.aion.gui.model;

/**
 * Contains API return codes. These are sourced from org.aion.api.impl.internal.Message and defined
 * by the protobuf file 'message.proto' in aion_api. Since Message isn't exported by aion_api, need
 * to duplicate the values here. If they are ever updated, this file must also be updated. TODO:
 * Have aion_api export the meaningful values so clients can use them.
 */
public class ApiReturnCodes {
    public static final int r_tx_Init_VALUE = 100;
    public static final int r_tx_Recved_VALUE = 101;
    public static final int r_tx_Dropped_VALUE = 102;
    public static final int r_tx_NewPending_VALUE = 103;
    public static final int r_tx_Pending_VALUE = 104;
    public static final int r_tx_Included_VALUE = 105;
}
