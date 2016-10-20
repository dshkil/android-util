/*
 * Copyright (C) 2016 Dmytro Shkil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shkil.android.util.concurrent;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

public final class MainThreadExecutor implements Executor {

    private static final MainThreadExecutor INSTANCE = new MainThreadExecutor();

    private static final Looper mainLooper = Looper.getMainLooper();
    private static final Handler handler = new Handler(mainLooper);

    private MainThreadExecutor() {
    }

    @Override
    public void execute(Runnable runnable) {
        if (isRunningOnMainThread()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    public void post(Runnable runnable) {
        handler.post(runnable);
    }

    public static Executor getInstance() {
        return INSTANCE;
    }

    protected static boolean isRunningOnMainThread() {
        return Thread.currentThread() == mainLooper.getThread();
    }

}
