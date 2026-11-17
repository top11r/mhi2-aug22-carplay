// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.store;

import com.mhi2.settings.Log;
import java.io.InputStream;
import java.util.LinkedList;

/*
 * Runs the few fixed commands the menu needs (remount /mnt/app, slay mm-ipod)
 * on one worker thread, one at a time, never on the HMI thread. Commands are
 * argument arrays: no shell, no user text.
 */
public final class Commands {
    private static final long TIMEOUT_MS = 10000;
    private static final LinkedList QUEUE = new LinkedList();
    private static Thread worker;

    private Commands() {
    }

    /* Queues a job for the worker thread. */
    public static synchronized void submit(Runnable job) {
        synchronized (QUEUE) {
            QUEUE.addLast(job);
            QUEUE.notifyAll();
        }
        if (worker == null) {
            worker = new Thread("mmi_settings commands") {
                public void run() {
                    loop();
                }
            };
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void loop() {
        while (true) {
            Runnable job;
            synchronized (QUEUE) {
                while (QUEUE.isEmpty()) {
                    try {
                        QUEUE.wait();
                    } catch (InterruptedException e) {
                        /* keep serving */
                    }
                }
                job = (Runnable)QUEUE.removeFirst();
            }
            try {
                job.run();
            } catch (Throwable t) {
                Log.log("commands: job failed: " + t);
            }
        }
    }

    /* Runs argv and returns its exit code, or -1 if it could not run or timed out. Worker thread only. */
    public static int run(String[] argv) {
        String line = join(argv);
        final Process process;
        try {
            process = Runtime.getRuntime().exec(argv);
        } catch (Throwable t) {
            Log.log("commands: " + line + " could not start: " + t);
            return -1;
        }
        final int[] exit = {-1};
        Thread waiter = new Thread("mmi_settings wait") {
            public void run() {
                try {
                    exit[0] = process.waitFor();
                } catch (InterruptedException e) {
                    /* timed out below */
                }
            }
        };
        waiter.setDaemon(true);
        waiter.start();
        try {
            waiter.join(TIMEOUT_MS);
        } catch (InterruptedException e) {
            /* treat as timeout */
        }
        String output = read(process.getInputStream()) + read(process.getErrorStream());
        if (waiter.isAlive()) {
            process.destroy();
            Log.log("commands: " + line + " timed out" + (output.length() > 0 ? ": " + output : ""));
            return -1;
        }
        Log.log("commands: " + line + " -> " + exit[0] + (output.length() > 0 ? ": " + output : ""));
        return exit[0];
    }

    private static String read(InputStream in) {
        StringBuffer text = new StringBuffer();
        try {
            byte[] buffer = new byte[256];
            while (in.available() > 0 && text.length() < 512) {
                int n = in.read(buffer);
                if (n <= 0) {
                    break;
                }
                text.append(new String(buffer, 0, n));
            }
            in.close();
        } catch (Throwable t) {
            /* output is only for the log */
        }
        return text.toString().trim();
    }

    private static String join(String[] argv) {
        StringBuffer line = new StringBuffer();
        for (int i = 0; i < argv.length; i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(argv[i]);
        }
        return line.toString();
    }
}
