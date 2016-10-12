/*
 * Copyright (c) 2015-2016 Dmytro Shkil
 * Copyright (c) 2015 Netpulse
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.shkil.android.util.logging;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class AndroidLoggerAdapter implements Logger {

    private static final String TAG = "AndroidLoggerAdapter";

    private final String name;

    private static int level = Log.VERBOSE;
    private static boolean skipLoggabilityCheck;

    public static synchronized int setLevel(int level) {
        int oldValue = AndroidLoggerAdapter.level;
        AndroidLoggerAdapter.level = level;
        return oldValue;
    }

    public static synchronized boolean setSkipLoggabilityCheck(boolean skip) {
        boolean oldValue = AndroidLoggerAdapter.skipLoggabilityCheck;
        AndroidLoggerAdapter.skipLoggabilityCheck = skip;
        return oldValue;
    }

    protected AndroidLoggerAdapter(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return isLoggable(Log.VERBOSE);
    }

    @Override
    public void trace(String msg) {
        log(Log.VERBOSE, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        formatAndLog(Log.VERBOSE, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        formatAndLog(Log.VERBOSE, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... argArray) {
        formatAndLog(Log.VERBOSE, format, argArray);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(Log.VERBOSE, msg, t);
    }

    @Override
    public void trace(Throwable ex) {
        log(Log.VERBOSE, "", ex);
    }

    @Override
    public boolean isDebugEnabled() {
        return isLoggable(Log.DEBUG);
    }

    @Override
    public void debug(String msg) {
        log(Log.DEBUG, msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        formatAndLog(Log.DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        formatAndLog(Log.DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... argArray) {
        formatAndLog(Log.DEBUG, format, argArray);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(Log.VERBOSE, msg, t);
    }

    @Override
    public void debug(Throwable ex) {
        log(Log.VERBOSE, "", ex);
    }

    @Override
    public boolean isInfoEnabled() {
        return isLoggable(Log.INFO);
    }

    @Override
    public void info(String msg) {
        log(Log.INFO, msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        formatAndLog(Log.INFO, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        formatAndLog(Log.INFO, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... argArray) {
        formatAndLog(Log.INFO, format, argArray);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(Log.INFO, msg, t);
    }

    @Override
    public void info(Throwable ex) {
        log(Log.INFO, "", ex);
    }

    @Override
    public boolean isWarnEnabled() {
        return isLoggable(Log.WARN);
    }

    @Override
    public void warn(String msg) {
        log(Log.WARN, msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        formatAndLog(Log.WARN, format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        formatAndLog(Log.WARN, format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... argArray) {
        formatAndLog(Log.WARN, format, argArray);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(Log.WARN, msg, t);
    }

    @Override
    public void warn(Throwable ex) {
        log(Log.WARN, "", ex);
    }

    @Override
    public boolean isErrorEnabled() {
        return isLoggable(Log.ERROR);
    }

    @Override
    public void error(String msg) {
        log(Log.ERROR, msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        formatAndLog(Log.ERROR, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        formatAndLog(Log.ERROR, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... argArray) {
        formatAndLog(Log.ERROR, format, argArray);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(Log.ERROR, msg, t);
    }

    @Override
    public void error(Throwable ex) {
        log(Log.ERROR, "", ex);
    }

    private void formatAndLog(int priority, String format, Object... args) {
        if (isLoggable(priority)) {
            Throwable throwable = getThrowableCandidate(args);
            String message = MessageFormatter.formatMessage(format, args);
            printLog(priority, message, throwable);
        }
    }

    private void log(int priority, String message, Throwable throwable) {
        if (isLoggable(priority)) {
            printLog(priority, message, throwable);
        }
    }

    private boolean isLoggable(int priority) {
        return priority >= level && (skipLoggabilityCheck || isLoggableNative(name, priority));
    }

    private static boolean isLoggableNative(String tag, int priority) {
        if (tag.length() > 23) {
            Log.w(TAG, "Tag " + tag + " length is more than 23. Assumed as not loggable.");
            return false;
        }
        return Log.isLoggable(tag, priority);
    }

    private void printLog(int priority, String message, Throwable throwable) {
        if (throwable != null) {
            message += '\n' + getStackTraceString(throwable);
        }
        Log.println(priority, name, message);
    }

    private static Throwable getThrowableCandidate(Object[] args) {
        if (args == null) {
            return null;
        }
        int length = args.length;
        if (length > 0) {
            Object lastEntry = args[length - 1];
            if (lastEntry instanceof Throwable) {
                return (Throwable) lastEntry;
            }
        }
        return null;
    }

    private static String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }

}