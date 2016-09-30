/*
 * Copyright (c) 2015-2016 Dmytro Shkil
 * Copyright (c) 2015 Netpulse
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 * <p/>
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 * <p/>
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 * <p/>
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.shkil.android.util.logging;

import android.util.Log;

public class LoggerFactory {

    private static final String TAG = LoggerFactory.class.getSimpleName();

    private static boolean slf4jLoggerFactoryAvailable = getClassByName("org.slf4j.LoggerFactory") != null;

    static {
        if (slf4jLoggerFactoryAvailable) {
            Log.d(formatName(TAG), "org.slf4j.LoggerFactory found.");
        }
    }

    private static String formatName(String name) {
        return name;
    }

    public static Logger getLogger(String name) {
        if (slf4jLoggerFactoryAvailable) {
            return new Slf4jLoggerAdapter(formatName(name));
        }
        return new AndroidLoggerAdapter(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }

    public static Logger getLogger(Class<?> clazz, Class<?> topClazz) {
        if (clazz == topClazz) {
            return getLogger(clazz.getSimpleName());
        }
        return getLogger(topClazz.getSimpleName() + "(" + clazz.getSimpleName() + ")");
    }

    private static Class<?> getClassByName(String className) {
        try {
            return Class.forName(className);
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

}
