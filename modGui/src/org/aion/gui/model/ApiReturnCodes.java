package org.aion.gui.model;

/**
 * Contains API return codes.  These are sourced from org.aion.api.impl.internal.Message and defined
 * by the protobuf file 'message.proto' in aion_api.  Since Message isn't exported by aion_api, need
 * to duplicate the values here.  If they are ever updated, this file must also be updated.  TODO:
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