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
package com.shkil.android.util.cache;

import com.google.auto.value.AutoValue;

import java.util.concurrent.TimeUnit;

@AutoValue
public abstract class CacheControl {

    public static final CacheControl INFINITE = builder().build();
    public static final CacheControl CACHE_ONLY = builder().cacheOnly(true).build();
    public static final CacheControl NO_CACHE = builder().maxAgeSeconds(0).maxStaleSeconds(0).build();
    public static final CacheControl REFRESH = builder().maxAgeSeconds(0).build();

    public abstract boolean cacheOnly();

    public abstract boolean noStore();

    public abstract int maxAgeSeconds();

    public abstract int maxStaleSeconds();

    public abstract Builder toBuilder();

    public boolean isCacheAllowed() {
        return maxAgeSeconds() > 0 || maxStaleSeconds() > 0;
    }

    public boolean isCacheDisallowed() {
        return maxAgeSeconds() <= 0 && maxStaleSeconds() <= 0;
    }

    public boolean isTimeLimited() {
        return maxAgeSeconds() < Integer.MAX_VALUE || maxStaleSeconds() < Integer.MAX_VALUE;
    }

    public static Builder builder() {
        return new AutoValue_CacheControl.Builder()
                .cacheOnly(false)
                .noStore(false)
                .maxAgeSeconds(Integer.MAX_VALUE)
                .maxStaleSeconds(Integer.MAX_VALUE);
    }

    @AutoValue.Builder
    public static abstract class Builder {
        public Builder noStore() {
            return noStore(true);
        }

        public abstract Builder noStore(boolean noStore);

        public Builder cacheOnly() {
            return cacheOnly(true);
        }

        public abstract Builder cacheOnly(boolean onlyIfCached);

        public Builder maxAge(int maxAge, TimeUnit timeUnit) {
            long seconds = timeUnit.toSeconds(maxAge);
            return maxAgeSeconds(seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds);
        }

        public abstract Builder maxAgeSeconds(int maxAgeSeconds);

        public Builder maxStale(int maxStale, TimeUnit timeUnit) {
            long seconds = timeUnit.toSeconds(maxStale);
            return maxStaleSeconds(seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds);
        }

        public abstract Builder maxStaleSeconds(int maxStaleSeconds);

        public abstract CacheControl build();
    }
}
