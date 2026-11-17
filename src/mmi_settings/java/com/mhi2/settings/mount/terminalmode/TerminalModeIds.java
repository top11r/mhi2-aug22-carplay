// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount.terminalmode;

import com.mhi2.settings.Settings;
import com.mhi2.settings.Texts;
import com.mhi2.settings.mount.HmiLanguage;

/*
 * Ids of the CarPlay settings in the Audi smartphone interface (module 32):
 * the entry row on the no-device screen, the "CarPlay settings" screen, its
 * nested "Route guidance" and "Virtual cockpit map" screens and the "takes effect
 * later" note popup. One model, label and focus id per Settings.ALL entry.
 * States and transitions are not here: they are appended after the stock
 * arrays at run time (TerminalModeSMMExt).
 */
public final class TerminalModeIds {
    public static final int MODULE = 32;

    /* Stock: the no-device screen and the event of its "Switch to connection manager" row. */
    public static final int SCREEN_NO_DEVICE = 3200006;
    public static final int EVENT_TO_CONNECTION_MANAGER = 3200007;

    /* Stock screens are 3200000..3200009; the factory caches nothing by id. Indexed by Settings.SCREEN_*. */
    public static final int[] SCREENS = {3200010, 3200011, 3200012};

    /* Events of our rows; unused by the stock state machine. Index = Settings.SCREEN_* (0: open main). */
    public static final int[] EVENTS_OPEN = {3209001, 3209002, 3209003};

    /* The stock bank has indices 0..52: Settings.ALL[i] is model FIRST_MODEL + i, then the restart action. */
    public static final int FIRST_MODEL = 3200053;
    public static final int MODEL_RESTART = FIRST_MODEL + Settings.ALL.length;

    /* Partial popups (stock module 32 has only 3200007) and their texts: the "after the MMI restarts"
     * note, "CarPlay restarted", "could not be saved". No "next phone plug" note: the menu is only
     * reached with no phone connected, so every change applies at the next plug anyway. */
    public static final int MMI_RESTART_NOTE = 0;
    public static final int RESTARTED = 1;
    public static final int NOT_SAVED = 2;
    public static final int[] POPUPS_NOTE = {3209202, 3209203, 3209204};

    /* Past the stock text table (ids up to 3200096). Screen titles indexed by Settings.SCREEN_*. */
    public static final int TEXT_ENTRY_ROW = 3209000;
    public static final int[] TEXT_SCREENS = {3209001, 3209002, 3209003};
    public static final int TEXT_RESTART = 3209004;
    public static final int[] TEXTS_NOTE = {3209006, 3209008, 3209009};
    public static final int FIRST_TEXT_SETTING = 3209010;
    public static final int FIRST_TEXT_HINT = 3209030;

    /* Focus-restore keys, unique per screen. Submenu rows indexed by Settings.SCREEN_* (0 unused). */
    public static final int INTERNAL_ENTRY_ROW = 3209100;
    public static final int[] INTERNAL_SUBMENUS = {0, 3209101, 3209102};
    public static final int INTERNAL_RESTART = 3209103;
    public static final int FIRST_INTERNAL_SETTING = 3209110;

    /* Cockpit map installed = its submenu is shown (Settings.RGD_JAR for Route guidance). */
    public static final String COCKPIT_MAP_FLAG = "altscreen_inject";

    /* Set by TerminalModeScreenFactoryExt; the bank needs it to raise the note popup. */
    static de.audi.atip.base.IFrameworkAccess framework;

    /* SMEventSystem.HK_RETURN, JS_FASTBACK */
    public static final int HK_RETURN = 1741;
    public static final int JS_FASTBACK = 1742;

    /* Transition.ACTION_FLAG; Transition target flags as stock forward/back. */
    public static final int TRANS_ACTION = 8;
    public static final int TARGET_FORWARD = 72;
    public static final int TARGET_BACK = 8;

    /* SMServices.addScreenAnimation values stock uses for forward/back. */
    public static final int ANIMATION_FORWARD = 2;
    public static final int ANIMATION_BACK = 4;

    private TerminalModeIds() {
    }

    static String text(int id) {
        String key = key(id);
        return key == null ? null : Texts.get(key, HmiLanguage.iso());
    }

    /* Our text id -> its key in i18n/*.json. */
    static String key(int id) {
        if (id == TEXT_ENTRY_ROW) {
            return "entry_row";
        }
        if (id == TEXT_SCREENS[Settings.SCREEN_MAIN]) {
            return "screen.main";
        }
        if (id == TEXT_SCREENS[Settings.SCREEN_ROUTE_GUIDANCE]) {
            return "screen.route_guidance";
        }
        if (id == TEXT_SCREENS[Settings.SCREEN_COCKPIT_MAP]) {
            return "screen.cockpit_map";
        }
        if (id == TEXT_RESTART) {
            return "restart";
        }
        if (id == TEXTS_NOTE[MMI_RESTART_NOTE]) {
            return "note.mmi_restart";
        }
        if (id == TEXTS_NOTE[RESTARTED]) {
            return "note.restarted";
        }
        if (id == TEXTS_NOTE[NOT_SAVED]) {
            return "note.not_saved";
        }
        int setting = id - FIRST_TEXT_SETTING;
        if (setting >= 0 && setting < Settings.ALL.length) {
            return Settings.ALL[setting].flag;
        }
        int hint = id - FIRST_TEXT_HINT;
        return hint >= 0 && hint < Settings.ALL.length ? Settings.ALL[hint].hintKey() : null;
    }
}
