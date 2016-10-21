package com.shkil.android.util.event;

import android.util.Log;

public abstract class AbstractEvent implements IEvent {

    private static int defaultLogLevel = Log.VERBOSE;

    public static void setDefaultLogLevel(int level) {
        AbstractEvent.defaultLogLevel = level;
    }

    @Override
    public int getLogLevel() {
        return defaultLogLevel;
    }

}
