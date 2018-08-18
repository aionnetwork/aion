package org.aion.gui.util;

import org.aion.gui.model.dto.SyncInfoDto;

public class SyncStatusFormatter {

    private static final int SECONDS_IN_A_MINUTE = 60;
    private static final int SECONDS_IN_A_HOUR = 3600;
    private static final int SECONDS_IN_A_DAY = 86400;
    private static final String UNDEFINED = "Undefined";
    private static final String UP_TO_DATE = "Up to date";
    private static final int HOURS_IN_A_DAY = 24;
    private static final int MINUTES_IN_AN_HOUR = 60;
    private static final int SYNC_STATUS_DISPLAY_UNIT_LIMIT = 2;

    public static String formatSyncStatus(SyncInfoDto syncInfo) {
        if(syncInfo != null) {
            if(syncInfo.getNetworkBestBlkNumber() > 0) {
                long seconds = (syncInfo.getNetworkBestBlkNumber() - syncInfo.getChainBestBlkNumber())
                        * AionConstants.BLOCK_MINING_TIME_SECONDS;
                if((int) seconds < SECONDS_IN_A_MINUTE) {
                    return UP_TO_DATE;
                }
                return getSyncStatusBySeconds(seconds);
            }
            return UNDEFINED;
        }
        return UNDEFINED;
    }


    public static String formatSyncStatusByBlockNumbers(SyncInfoDto syncInfo) {
        return syncInfo.getChainBestBlkNumber() + "/" + syncInfo.getNetworkBestBlkNumber() + " total blocks";
    }

    private static String getSyncStatusBySeconds(long seconds) {
        int minutes = (int) seconds / SECONDS_IN_A_MINUTE;
        int hours = (int) seconds / SECONDS_IN_A_HOUR;
        int days = (int) seconds / SECONDS_IN_A_DAY;
        String syncStatus = "";
        int unitsDisplayed = 0;
        if(days > 0) {
            syncStatus += days + " days ";
            unitsDisplayed++;
        }
        if(hours > 0 && unitsDisplayed < SYNC_STATUS_DISPLAY_UNIT_LIMIT) {
            syncStatus += (hours - days * HOURS_IN_A_DAY) + " hours ";
            unitsDisplayed++;
        }
        if(minutes > 0 && unitsDisplayed < SYNC_STATUS_DISPLAY_UNIT_LIMIT) {
            syncStatus += (minutes - hours * MINUTES_IN_AN_HOUR) + " minutes ";
            unitsDisplayed++;
        }
        if((int) seconds > 0 && unitsDisplayed < SYNC_STATUS_DISPLAY_UNIT_LIMIT) {
            syncStatus += (seconds - minutes * SECONDS_IN_A_MINUTE) + " seconds";
        }
        return syncStatus;
    }
}
