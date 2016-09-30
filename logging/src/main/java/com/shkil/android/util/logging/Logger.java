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

public interface Logger {

    public String getName();
    public boolean isTraceEnabled();
    public void trace(String msg);
    public void trace(String format, Object arg);
    public void trace(String format, Object arg1, Object arg2);
    public void trace(String format, Object... arguments);
    public void trace(String msg, Throwable ex);
    public void trace(Throwable ex);
    public boolean isDebugEnabled();
    public void debug(String msg);
    public void debug(String format, Object arg);
    public void debug(String format, Object arg1, Object arg2);
    public void debug(String format, Object... arguments);
    public void debug(String msg, Throwable ex);
    public void debug(Throwable ex);
    public boolean isInfoEnabled();
    public void info(String msg);
    public void info(String format, Object arg);
    public void info(String format, Object arg1, Object arg2);
    public void info(String format, Object... arguments);
    public void info(String msg, Throwable ex);
    public void info(Throwable ex);
    public boolean isWarnEnabled();
    public void warn(String msg);
    public void warn(String format, Object arg);
    public void warn(String format, Object... arguments);
    public void warn(String format, Object arg1, Object arg2);
    public void warn(String msg, Throwable ex);
    public void warn(Throwable ex);
    public boolean isErrorEnabled();
    public void error(String msg);
    public void error(String format, Object arg);
    public void error(String format, Object arg1, Object arg2);
    public void error(String format, Object... arguments);
    public void error(String msg, Throwable ex);
    public void error(Throwable ex);

}