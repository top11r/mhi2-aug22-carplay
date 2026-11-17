// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings;

/*
 * The settings the menu shows: one row per flag file in /mnt/app that a
 * module reads (spec "Settings tree"). Order = row order on each screen.
 * Labels are texts keyed by the flag name, hints by "<flag>.hint" (i18n/*.json).
 * shortcut: the table is here, not loaded from descriptors in each hook jar
 * (design D5); move it out when a jar outside this repo needs its own rows.
 */
public final class Settings {
    public static final int SCREEN_MAIN = 0;
    public static final int SCREEN_ROUTE_GUIDANCE = 1;
    public static final int SCREEN_COCKPIT_MAP = 2;

    /* When a change takes effect. */
    public static final int LIVE = 0;
    public static final int NEXT_PLUG = 1;
    public static final int MMI_RESTART = 2;

    /* Route guidance installed: its rows and submenu are shown. */
    public static final String RGD_JAR = "/mnt/app/eso/hmi/lsd/jars/rgd_hook.jar";

    public static final Settings[] ALL = {
        /* off: librgd_hook.so not loaded, no maneuvers and no CarPlay map in the cockpit; RgdSwitch caches it per HMI start */
        new Settings("rgd_disable", false, MMI_RESTART, SCREEN_MAIN, RGD_JAR),
        /* off: no route guidance component in the iAP2 identification, the cockpit keeps the CarPlay map */
        new Settings("rgd_map_only", false, NEXT_PLUG, SCREEN_ROUTE_GUIDANCE, null),
        /* overrides the view read from the cluster (ClusterSkin): a workaround where detection fails */
        new Settings("rgd_sport", true, LIVE, SCREEN_ROUTE_GUIDANCE, null),
        new Settings("rgd_eta_default", true, LIVE, SCREEN_ROUTE_GUIDANCE, null),
        /* off: plain arrow; maneuver_render reads it when it starts (it runs until the ignition goes off) */
        new Settings("rgd_no_arrow_fill", false, MMI_RESTART, SCREEN_ROUTE_GUIDANCE, null),
        /* BAPBridge reads it once per route guidance session */
        new Settings("rgd_cancel_native", true, NEXT_PLUG, SCREEN_ROUTE_GUIDANCE, null),
        new Settings("altscreen_cluster_off", false, LIVE, SCREEN_COCKPIT_MAP, null),
        new Settings("altscreen_force", true, NEXT_PLUG, SCREEN_COCKPIT_MAP, null),
    };

    /* Flag file name in /mnt/app. */
    public final String flag;
    /* true: file present = ticked; false: file present = unticked (an "off" flag). */
    public final boolean presentMeansTicked;
    /* LIVE, NEXT_PLUG or MMI_RESTART; only MMI_RESTART raises a note (the menu is reached with no phone connected). */
    public final int effect;
    public final int screen;
    /* File that must exist for the row to be shown (the feature it belongs to), or null. */
    public final String requires;

    private Settings(String flag, boolean presentMeansTicked, int effect, int screen, String requires) {
        this.flag = flag;
        this.presentMeansTicked = presentMeansTicked;
        this.effect = effect;
        this.screen = screen;
        this.requires = requires;
    }

    /* Text key of the row's hint, or null if the row has none. */
    public String hintKey() {
        String key = this.flag + ".hint";
        return Texts.get(key, null) != null ? key : null;
    }

    public boolean shown() {
        return this.requires == null || new java.io.File(this.requires).exists();
    }

    public boolean ticked(boolean present) {
        return present == this.presentMeansTicked;
    }

    public boolean present(boolean ticked) {
        return ticked == this.presentMeansTicked;
    }
}
