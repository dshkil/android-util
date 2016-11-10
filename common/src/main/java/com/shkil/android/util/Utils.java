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
package com.shkil.android.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.util.TypedValue;

import com.shkil.android.util.common.BuildConfig;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {

    private static class MainThreadReferenceHolder { // Lazy Holder idiom class
        static final Thread MAIN_THREAD = getMainThread();

        private static Thread getMainThread() {
            try {
                return Looper.getMainLooper().getThread();
            } catch (RuntimeException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("not mocked")) {
                    return null;
                }
                throw ex;
            }
        }
    }

    private static final boolean TESTING = MainThreadReferenceHolder.MAIN_THREAD == null || MainThreadReferenceHolder.MAIN_THREAD.getName().contains("test");

    private static final boolean ASSERT_MAIN_THREAD = BuildConfig.DEBUG && !TESTING; // workaround for tests

    public static boolean isRunningOnMainThread() {
        return Thread.currentThread() == MainThreadReferenceHolder.MAIN_THREAD;
    }

    public static void preventRunningOnMainThread() {
        if (ASSERT_MAIN_THREAD && Thread.currentThread() == MainThreadReferenceHolder.MAIN_THREAD) {
            throw new RuntimeException("Possibly long operation has been running in the main thread");
        }
    }

    public static void ensureRunningOnMainThread() {
        if (ASSERT_MAIN_THREAD && Thread.currentThread() != MainThreadReferenceHolder.MAIN_THREAD) {
            throw new RuntimeException("Should be running in the main thread");
        }
    }

    public static boolean isRunningInTest() {
        return TESTING;
    }

    public static boolean equal(Object o1, Object o2) {
        return o1 == o2 || (o1 != null && o1.equals(o2));
    }

    public static boolean notEqual(Object o1, Object o2) {
        return o1 != o2 && (o1 == null || !o1.equals(o2));
    }

    public static boolean isNotEmpty(CharSequence string) {
        return string != null && string.length() > 0;
    }

    public static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    public static boolean isBlank(CharSequence value) {
        if (value != null) {
            int length = value.length();
            for (int i = 0; i < length; i++) {
                if (value.charAt(i) != ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isNotEmpty(Collection<?> list) {
        return list != null && list.size() > 0;
    }

    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isEmpty(Collection<?> list) {
        return list == null || list.size() == 0;
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.size() == 0;
    }

    public static boolean isNotEmpty(Map<?, ?> map) {
        return map != null && map.size() > 0;
    }

    public static boolean isNotEmpty(Object[] array) {
        return array != null && array.length > 0;
    }

    public static <T> T nonNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : Collections.<T>emptyList();
    }

    public static <K, V> Map<K, V> nonNull(Map<K, V> list) {
        return list != null ? list : Collections.<K, V>emptyMap();
    }

    public static void checkNotEmpty(String value, String argumentName) throws IllegalArgumentException {
        if (isEmpty(value)) {
            throw new IllegalArgumentException(argumentName + " value is empty");
        }
    }

    public static void checkNotNull(Object value, String argumentName) throws IllegalArgumentException {
        if (value == null) {
            throw new IllegalArgumentException(argumentName + " value is null");
        }
    }

    public static int getSize(Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    public static int getSize(Object[] array) {
        return array != null ? array.length : 0;
    }

    public static int getSize(String string) {
        return string != null ? string.length() : 0;
    }

    /**
     * @param defaultValue can't be null
     */
    public static <T extends Enum<T>> T valueOf(int ordinal, T defaultValue) {
        if (defaultValue == null) {
            throw new IllegalArgumentException("defaultValue can't be null");
        }
        if (ordinal >= 0) {
            T[] values = defaultValue.getDeclaringClass().getEnumConstants();
            if (ordinal < values.length) {
                return values[ordinal];
            }
        }
        return defaultValue;
    }

    public static <T extends Enum<T>> T valueOf(int ordinal, T defaultValue, Class<T> enumClass) {
        if (ordinal >= 0) {
            T[] values = enumClass.getEnumConstants();
            if (ordinal < values.length) {
                return values[ordinal];
            }
        }
        return defaultValue;
    }

    public static <T extends Enum<T>> T valueOf(int ordinal, Class<T> enumClass) {
        if (ordinal >= 0) {
            T[] values = enumClass.getEnumConstants();
            if (ordinal < values.length) {
                return values[ordinal];
            }
        }
        throw new ArrayIndexOutOfBoundsException("ordinal");
    }

    public static <T extends Enum<T>> T valueOf(String name, Class<T> enumClass, T defaultValue) {
        if (isNotEmpty(name)) {
            for (T value : enumClass.getEnumConstants()) {
                if (name.equals(value.name())) {
                    return value;
                }
            }
        }
        return defaultValue;
    }

    public static <T> int indexOf(T item, T[] items) {
        for (int i = 0; i < items.length; i++) {
            if (item.equals(items[i])) {
                return i;
            }
        }
        return -1;
    }

    public static String urlEncode(String value) {
        if (isEmpty(value)) {
            return value;
        }
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String urlDecode(String value) {
        if (isEmpty(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, "utf-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param name name of thread, use {0} for thread counter
     */
    public static ThreadFactory newThreadFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, MessageFormat.format(name, counter.incrementAndGet()));
            }
        };
    }

    /**
     * @param name     name of thread, use {0} for thread counter
     * @param priority thread priority, see {@link android.os.Process}
     */
    public static ThreadFactory newThreadFactory(final String name, final int priority) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, MessageFormat.format(name, counter.incrementAndGet())) {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(priority);
                        super.run();
                    }
                };
            }
        };
    }

    public static int parseInt(String string, int defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static long parseLong(String string, long defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Long.parseLong(string);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static float parseFloat(String string, float defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(string);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static double parseDouble(String string, double defaultValue) {
        if (string == null || string.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(string);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static boolean parseBoolean(String value, boolean defaultValue) {
        if (value != null) {
            if ("1".equals(value) || "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
                return true;
            }
            if ("0".equals(value) || "no".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return defaultValue;
    }

    /**
     * Join collection elements with a delimiter string.
     */
    public static String join(String delimiter, Collection<String> values, boolean escapeStrings) {
        int valuesCount = values.size();
        if (valuesCount == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            if (escapeStrings) {
                DatabaseUtils.appendEscapedSQLString(sb, value);
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    /**
     * Join array elements with a delimiter string.
     */
    public static String join(String delimiter, String[] values, boolean escapeStrings) {
        int valuesCount = values.length;
        if (valuesCount == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            if (escapeStrings) {
                DatabaseUtils.appendEscapedSQLString(sb, value);
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    public static String toString(Bundle bundle) {
        if (bundle == null) {
            return "null";
        }
        if (bundle.isEmpty()) { // really needed to unparcel data
            return "Bundle[]";
        }
        return bundle.toString();
    }

    public static String trim(String value) {
        return value != null ? value.trim() : null;
    }

    public static String intern(String value) {
        return value != null ? value.intern() : null;
    }

    public static String getAppVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Bundle getAppMetaData(Context context) {
        ApplicationInfo appInfo;
        try {
            appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        return appInfo.metaData;
    }

    public static TypedValue getTypedValue(Resources res, int id, boolean resolveRefs) {
        TypedValue typedValue = new TypedValue();
        res.getValue(id, typedValue, resolveRefs);
        return typedValue;
    }

    public static IOException asIOException(Throwable ex) throws IOException {
        if (ex instanceof IOException) {
            return (IOException) ex;
        }
        return new IOException(ex);
    }

    public static RuntimeException asRuntimeException(Throwable ex) throws RuntimeException {
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new RuntimeException(ex);
    }

    public static Field getDeclaredField(Class<?> clazz, String fieldName, boolean forceAccessible) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            if (forceAccessible) {
                field.setAccessible(true);
            }
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static class FieldModifiersFieldHolder { // Lazy Holder idiom class
        static final Field VALUE = getDeclaredField(Field.class, "modifiers", true);
    }

    public static void clearFinalModifier(Field field) {
        try {
            if (FieldModifiersFieldHolder.VALUE != null) { // fix for Oracle Java
                int modifiers = field.getModifiers();
                if (Modifier.isFinal(modifiers)) {
                    FieldModifiersFieldHolder.VALUE.setInt(field, modifiers & ~Modifier.FINAL);
                }
            }
        } catch (Exception ex) {
            throw asRuntimeException(ex);
        }
    }

    public static void setFinalFieldValue(Object object, Field field, Object value) {
        try {
            clearFinalModifier(field);
            field.set(object, value);
        } catch (Exception ex) {
            throw asRuntimeException(ex);
        }
    }

    public static Bundle asBundle(String key, Object value) {
        Bundle result = new Bundle();
        if (value == null) {
            result.putSerializable(key, null);
        } else if (value instanceof Parcelable) {
            result.putParcelable(key, (Parcelable) value);
        } else if (value instanceof Serializable) {
            result.putSerializable(key, (Serializable) value);
        } else {
            throw new IllegalArgumentException("Wrong parameter type");
        }
        return result;
    }

    public static <K, V> HashMap<K, V> asHashMap(Map<K, V> map) {
        if (map == null || map instanceof HashMap) {
            return (HashMap<K, V>) map;
        }
        return new HashMap<K, V>(map);
    }

    public static <V> HashSet<V> asHashSet(Collection<V> collection) {
        if (collection == null || collection instanceof HashSet) {
            return (HashSet<V>) collection;
        }
        return new HashSet<V>(collection);
    }

    public static <T> T[] toArray(Collection<T> values, T[] emptyArray) {
        if (emptyArray == null || emptyArray.length > 0) {
            throw new IllegalArgumentException("emptyArray");
        }
        if (isEmpty(values)) {
            return emptyArray;
        }
        return values.toArray(emptyArray);
    }

    public static String calculateMd5Hash(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        StringBuilder buffer = new StringBuilder();
        calculateMd5Hash(value, buffer);
        return buffer.toString();
    }

    public static void calculateMd5Hash(String value, StringBuilder output) {
        if (value == null || value.length() == 0) {
            return;
        }
        try {
            MessageDigest hashEngine = MessageDigest.getInstance("MD5");
            hashEngine.update(value.getBytes());
            toHex(hashEngine.digest(), output);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String toHex(byte[] data) {
        return toHex(data, 0);
    }

    public static String toHex(byte[] data, int startIndex) {
        if (data == null || data.length == startIndex) {
            return "";
        }

        if (startIndex > data.length) {
            throw new ArrayIndexOutOfBoundsException("startIndex");
        }
        StringBuilder buffer = new StringBuilder((data.length - startIndex) * 2);
        toHex(data, startIndex, buffer);
        return buffer.toString();
    }

    public static void toHex(byte[] data, StringBuilder output) {
        toHex(data, 0, output);
    }

    public static void toHex(byte[] data, int startIndex, StringBuilder output) {
        if (data == null || data.length == startIndex) {
            return;
        }
        if (startIndex > data.length) {
            throw new ArrayIndexOutOfBoundsException("startIndex");
        }
        for (int i = startIndex; i < data.length; i++) {
            byte dataByte = data[i];
            int halfByte = (dataByte >>> 4) & 0x0F;
            int twoHalves = 0;
            do {
                if (halfByte >= 0 && halfByte <= 9) {
                    output.append((char) ('0' + halfByte));
                } else {
                    output.append((char) ('a' + halfByte - 10));
                }
                halfByte = dataByte & 0x0F;
            }
            while (twoHalves++ < 1);
        }
    }


}
