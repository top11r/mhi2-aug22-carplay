// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.store;

import com.mhi2.settings.Log;
import java.io.File;
import java.io.FileOutputStream;

/*
 * Settings are the flag files in /mnt/app that the modules read (design D4).
 * Reading is File.exists(). A change is written directly when /mnt/app is
 * already writable (altscreen_sup.sh opens it for a CarPlay session);
 * otherwise /mnt/app is remounted read-write for that one create/delete and
 * read-only again right after. The mount is left as it was found. Writes run
 * on the Commands worker.
 */
public final class FlagStore {
    public static final String DIR = "/mnt/app";
    private static final String MOUNT = "/bin/mount";
    private static final int READ_ONLY_RETRIES = 3;
    /* altscreen_sup.sh writes it, then opens /mnt/app for the rest of the boot (roller long press). */
    private static final String SESSION_OPENED = "/dev/shmem/altscreen_sup.pid";

    /* Told the flag's state after a write attempt (whatever the outcome). */
    public interface Done {
        void written(boolean present);
    }

    private FlagStore() {
    }

    public static boolean isPresent(String flag) {
        return new File(DIR, flag).exists();
    }

    /* Creates (present) or deletes the flag; calls done on the worker thread with the file's state afterwards. */
    public static void set(final String flag, final boolean present, final Done done) {
        Commands.submit(new Runnable() {
            public void run() {
                write(flag, present);
                boolean now = isPresent(flag);
                Log.log("flags: " + flag + (now ? " present" : " absent")
                    + (now == present ? "" : " - write failed, wanted " + (present ? "present" : "absent")));
                if (done != null) {
                    done.written(now);
                }
            }
        });
    }

    private static void write(String flag, boolean present) {
        if (isPresent(flag) == present || change(flag, present)) {
            return;
        }
        if (Commands.run(new String[] {MOUNT, "-uw", DIR}) != 0) {
            Log.log("flags: " + DIR + " cannot be remounted, " + flag + " not changed");
            return;
        }
        try {
            if (!change(flag, present)) {
                Log.log("flags: " + flag + " not changed although " + DIR + " was remounted");
            }
        } finally {
            readOnly();
        }
    }

    /* Creates or deletes the file; false if the file system refused (read-only). */
    private static boolean change(String flag, boolean present) {
        File file = new File(DIR, flag);
        try {
            if (present) {
                new FileOutputStream(file).close();
                return true;
            }
            return file.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void readOnly() {
        if (new File(SESSION_OPENED).exists()) {
            /* a CarPlay session started while we wrote: leave /mnt/app open, as the supervisor wants it */
            Log.log("flags: " + DIR + " left writable, altscreen_sup.sh has opened it");
            return;
        }
        for (int i = 0; i < READ_ONLY_RETRIES; i++) {
            if (Commands.run(new String[] {MOUNT, "-ur", DIR}) == 0) {
                return;
            }
        }
        Log.log("flags: " + DIR + " could not be made read-only again");
    }
}
