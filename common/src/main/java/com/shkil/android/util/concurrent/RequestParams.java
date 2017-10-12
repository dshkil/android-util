/*
 * Copyright (C) 2017 Dmytro Shkil
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.shkil.android.util.cache.CacheControl;

@AutoValue
public abstract class RequestParams {

    public static final RequestParams DEFAULT;
    public static final RequestParams PRIORITY_IMMEDIATE;
    public static final RequestParams PRIORITY_NORMAL;
    public static final RequestParams PRIORITY_BACKGROUND;

    static {
        Builder defaultBuilder = builder();
        DEFAULT = defaultBuilder.build();
        PRIORITY_IMMEDIATE = defaultBuilder.priority(Priority.IMMEDIATE).build();
        PRIORITY_NORMAL = defaultBuilder.priority(Priority.NORMAL).build();
        PRIORITY_BACKGROUND = defaultBuilder.priority(Priority.BACKGROUND).build();
    }

    @NonNull
    public abstract Priority priority();

    @NonNull
    public abstract CacheControl cacheControl();

    /**
     * Allow interim results
     */
    public abstract boolean allowInterim();

    public abstract Builder toBuilder();

    public static Builder builder() {
        return new AutoValue_RequestParams.Builder()
                .allowInterim(false)
                .priority(Priority.NORMAL)
                .cacheControl(CacheControl.INFINITE);
    }

    public static RequestParams priority(@Nullable Priority priority) {
        if (priority == null) {
            return null;
        }
        switch (priority) {
            case IMMEDIATE:
                return PRIORITY_IMMEDIATE;
            case NORMAL:
                return PRIORITY_NORMAL;
            case BACKGROUND:
                return PRIORITY_BACKGROUND;
        }
        throw new IllegalArgumentException();
    }

    public static Builder cacheControl(CacheControl.Builder cacheControl) {
        return cacheControl(cacheControl.build());
    }

    public static Builder cacheControl(CacheControl cacheControl) {
        return builder().cacheControl(cacheControl);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder priority(Priority priority);

        public Builder cacheControl(CacheControl.Builder cacheControl) {
            return cacheControl(cacheControl.build());
        }

        public abstract Builder cacheControl(CacheControl cacheControl);

        /**
         * Allow interim results
         */
        public Builder allowInterim() {
            return allowInterim(true);
        }

        public abstract Builder allowInterim(boolean allowIntermediate);

        public abstract RequestParams build();
    }
}
