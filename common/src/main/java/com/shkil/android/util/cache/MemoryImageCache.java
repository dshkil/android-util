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
package com.shkil.android.util.cache;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;

public class MemoryImageCache<K> extends LruCache<K,Bitmap> {

    public static final int DEFAULT_MAX_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 6);

    public static <K> MemoryImageCache<K> newInstance() {
        return new MemoryImageCache<K>();
    }

    public static <K> MemoryImageCache<K> newInstance(int maxSize) {
        return new MemoryImageCache<K>(maxSize);
    }

    protected MemoryImageCache() {
        super(DEFAULT_MAX_CACHE_SIZE);
    }

    protected MemoryImageCache(int maxSize) {
        super(maxSize);
    }

    @Override
    @TargetApi(12)
    protected int sizeOf(K key, Bitmap value) {
        if (value == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            return value.getByteCount();
        }
        return value.getRowBytes() * value.getHeight();
    }

}
