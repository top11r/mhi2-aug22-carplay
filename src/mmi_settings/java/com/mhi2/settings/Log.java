// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings;

/* The HMI's log channels are not collected; the library writes its own file. */
public final class Log {
    public static final String FILE = "/tmp/mmi_settings.log";

    private Log() {
    }

    public static synchronized void log(String message) {
        java.io.FileOutputStream out = null;
        try {
            out = new java.io.FileOutputStream(FILE, true);
            out.write((System.currentTimeMillis() + " " + message + "\n").getBytes());
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
