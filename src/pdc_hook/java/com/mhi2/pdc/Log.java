// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.pdc;

/* The HMI's log channels are not collected; the hook writes its own file in RAM.
 * Warnings always; the parking events only with the trace marker chefranov's
 * hooks use, so one file switches tracing on for all of them. */
public final class Log {
    public static final String FILE = "/tmp/pdc_hook.log";
    private static final long MAX_BYTES = 256L * 1024L;
    private static final boolean TRACE = exists("/mnt/app/.mhi2_trace") || exists("/mnt/app/.mhi2_trace_all");

    private Log() {
    }

    private static boolean exists(String path) {
        try {
            return new java.io.File(path).exists();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void i(String tag, String message) {
        if (TRACE) write(tag + " " + message);
    }

    public static void w(String tag, String message) {
        write(tag + " WARN " + message);
    }

    private static synchronized void write(String line) {
        java.io.FileOutputStream out = null;
        try {
            if (new java.io.File(FILE).length() > MAX_BYTES) return;
            out = new java.io.FileOutputStream(FILE, true);
            out.write((System.currentTimeMillis() + " " + line + "\n").getBytes());
        } catch (Throwable t) {
            /* nowhere else to report it */
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable t) {
                    /* closed or not, nothing to do */
                }
            }
        }
    }
}
