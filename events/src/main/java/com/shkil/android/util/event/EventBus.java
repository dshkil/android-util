package com.shkil.android.util.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.shkil.android.util.Utils;
import com.shkil.android.util.events.BuildConfig;
import com.squareup.otto.Bus;

public class EventBus {

    private static final String TAG = "EventBus";

    protected static final String BROADCAST_PERMISSION_SUFFIX = ".permission.EVENT_BUS";
    protected static final String BROADCAST_ACTION_SUFFIX = ".broadcast.EVENT_BUS";

    private static final boolean DEFAULT_TRACE_APP_EVENTS = BuildConfig.DEBUG;

    public static abstract class EventReceiver extends BroadcastReceiver {
        protected abstract EventBus getEventBus(Context context);

        @Override
        public void onReceive(Context context, Intent intent) {
            getEventBus(context).onReceive(intent);
        }
    }

    private static final int MSG_POST_EVENT = 1;

    private final Context context;
    private final boolean supportEventBroadcasting;
    private final boolean traceAppEvents;

    private final Bus eventBus = new Bus();
    private final String eventBroadcastPermission;
    private final String eventBroadcastAction;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POST_EVENT:
                    eventBus.post(msg.obj);
                    break;
            }
        }
    };

    private final BroadcastReceiver eventBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            EventBus.this.onReceive(intent);
        }
    };

    public EventBus(Context context, boolean supportEventBroadcasting) {
        this(context, supportEventBroadcasting, DEFAULT_TRACE_APP_EVENTS);
    }

    public EventBus(Context context, boolean supportEventBroadcasting, boolean traceAppEvents) {
        this.context = context.getApplicationContext();
        this.supportEventBroadcasting = supportEventBroadcasting;
        this.traceAppEvents = traceAppEvents;
        if (supportEventBroadcasting) {
            String packageName = context.getPackageName();
            this.eventBroadcastPermission = packageName + BROADCAST_PERMISSION_SUFFIX;
            this.eventBroadcastAction = packageName + BROADCAST_ACTION_SUFFIX;
            IntentFilter eventBroadcastFilter = new IntentFilter(eventBroadcastAction);
            context.registerReceiver(eventBroadcastReceiver, eventBroadcastFilter);
        } else {
            this.eventBroadcastPermission = null;
            this.eventBroadcastAction = null;
        }
    }

    protected void onReceive(Intent intent) {
        if (intent.getIntExtra("senderProcessId", 0) == Process.myPid()) {
            return;
        }
        IBroadcastEvent event = intent.getParcelableExtra("event");
        if (event == null) {
            throw new IllegalArgumentException("event == null");
        }
        post(event, false);
    }

    public void post(IEvent event) {
        post(event, true);
    }

    private void post(IEvent event, boolean original) {
        if (original && traceAppEvents) {
            int priority = event.getLogLevel();
            if (priority > 0) {
                //noinspection WrongConstant
                Log.println(priority, TAG, "post: " + event);
            }
        }
        if (Utils.isRunningOnMainThread()) {
            eventBus.post(event);
        } else {
            Message.obtain(handler, MSG_POST_EVENT, event).sendToTarget();
        }
        if (supportEventBroadcasting && original && event instanceof IBroadcastEvent) {
            Intent intent = new Intent(eventBroadcastAction)
                    .putExtra("event", (IBroadcastEvent) event)
                    .putExtra("senderProcessId", Process.myPid());
            context.sendBroadcast(intent, eventBroadcastPermission);
        }
    }

    public void register(Object object) {
        eventBus.register(object);
    }

    public void unregister(Object object) {
        eventBus.unregister(object);
    }

}
