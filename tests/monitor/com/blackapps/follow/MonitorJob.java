package com.blackapps.follow;

import android.content.Context;

/** Test-only stub for Monitor's strict completion scheduling hooks. */
public final class MonitorJob {
    public static int strictSchedules,strictCancels;
    public static void scheduleStrict(Context context){strictSchedules++;}
    public static void cancelStrict(Context context){strictCancels++;}
}
