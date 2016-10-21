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

public final class MainThread {

    public static final Looper LOOPER = Looper.getMainLooper();
    public static final Handler HANDLER = new Handler(LOOPER);
    public static final MainThreadExecutor EXECUTOR = MainThreadExecutor.getInstance();

    private MainThread() {
    }

}
