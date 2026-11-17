// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount.terminalmode;

import com.mhi2.settings.Log;
import com.mhi2.settings.Settings;
import com.mhi2.settings.mount.SmmArrays;
import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.statemachine.SMServices;
import de.audi.tghu.terminalmode.sm.TerminalModeSMM;
import java.util.HashMap;

/*
 * The smartphone interface's state machine plus our screens, reached from the
 * no-device screen (next to the stock states):
 *   no-device state --open main--> main A                    (forward)
 *   main A --Back--> no-device state                         (back)
 *   main A --open sub--> sub A, sub A --Back--> main A       (for each submenu screen)
 *
 * Only appends to the stock arrays; if anything throws, the stock arrays stay.
 * init() runs inside the AbstractSMM constructor, so the fields it sets must
 * have no initialisers (they would run afterwards and reset them).
 */
public class TerminalModeSMMExt extends TerminalModeSMM {
    /* Our transition id -> name for the log; animation from the name ("open"/"back"). */
    private HashMap names;

    public TerminalModeSMMExt(IFrameworkAccess framework, int terminal, String name) {
        super(framework, terminal, name);
    }

    public TerminalModeSMMExt(IFrameworkAccess framework, int terminal, String name, int subterminal) {
        super(framework, terminal, name, subterminal);
    }

    protected void init() {
        super.init();
        int[] superstates = this.stateSuperstateList;
        int[] screens = this.stateDHSList;
        int[] flags = this.stateFlagList;
        int[][] triggers = this.stateTriggerEventList;
        int[][] outs = this.stateOutTransList;
        int[][] mediators = this.stateMediatorList;
        int[][] syncTriggers = this.stateSyncTriggerList;
        int[][] syncExitTriggers = this.stateSyncExitTriggerList;
        int[][] syncTargets = this.stateSyncTargetList;
        int[] transFlags = this.transFlagList;
        int[][] transTargets = this.transTrgtStateList;
        int[][] transTargetFlags = this.transTrgtFlagList;
        try {
            this.names = new HashMap();
            extend();
            Log.log("smm " + this.getTerminalName() + ": states " + (base() + flags.length) + "-"
                + (base() + this.stateFlagList.length - 1) + ", transitions " + (base() + transFlags.length) + "-"
                + (base() + this.transFlagList.length - 1));
        } catch (Throwable t) {
            this.stateSuperstateList = superstates;
            this.stateDHSList = screens;
            this.stateFlagList = flags;
            this.stateTriggerEventList = triggers;
            this.stateOutTransList = outs;
            this.stateMediatorList = mediators;
            this.stateSyncTriggerList = syncTriggers;
            this.stateSyncExitTriggerList = syncExitTriggers;
            this.stateSyncTargetList = syncTargets;
            this.transFlagList = transFlags;
            this.transTrgtStateList = transTargets;
            this.transTrgtFlagList = transTargetFlags;
            this.names = null;
            Log.log("smm " + this.getTerminalName() + ": left stock: " + t);
        }
    }

    private int base() {
        return TerminalModeIds.MODULE * 100000;
    }

    private void extend() {
        int from = SmmArrays.indexOf(this.stateDHSList, TerminalModeIds.SCREEN_NO_DEVICE);
        int noDevice = base() + from;
        int superstate = this.stateSuperstateList[from];
        int[] back = {TerminalModeIds.HK_RETURN, TerminalModeIds.JS_FASTBACK};

        int mainA = addState(superstate, TerminalModeIds.SCREENS[Settings.SCREEN_MAIN], 0);
        int open = addTransition(TerminalModeIds.TRANS_ACTION, mainA, TerminalModeIds.TARGET_FORWARD, "open settings");
        trigger(noDevice, new int[] {TerminalModeIds.EVENTS_OPEN[Settings.SCREEN_MAIN]}, open);
        trigger(mainA, back, addTransition(TerminalModeIds.TRANS_ACTION, noDevice, TerminalModeIds.TARGET_BACK, "back from settings"));
        submenus(mainA, superstate);
    }

    /* Route guidance and Cockpit map below a main state; the factory hides the rows of features not installed. */
    private void submenus(int main, int superstate) {
        int[] back = {TerminalModeIds.HK_RETURN, TerminalModeIds.JS_FASTBACK};
        for (int screen = Settings.SCREEN_ROUTE_GUIDANCE; screen <= Settings.SCREEN_COCKPIT_MAP; screen++) {
            int sub = addState(superstate, TerminalModeIds.SCREENS[screen], 0);
            trigger(main, new int[] {TerminalModeIds.EVENTS_OPEN[screen]},
                addTransition(TerminalModeIds.TRANS_ACTION, sub, TerminalModeIds.TARGET_FORWARD, "open screen " + screen));
            trigger(sub, back,
                addTransition(TerminalModeIds.TRANS_ACTION, main, TerminalModeIds.TARGET_BACK, "back from screen " + screen));
        }
    }

    /* Appends a state without triggers; returns its id. */
    private int addState(int superstate, int screen, int flags) {
        int id = base() + this.stateFlagList.length;
        this.stateSuperstateList = SmmArrays.append(this.stateSuperstateList, superstate);
        this.stateDHSList = SmmArrays.append(this.stateDHSList, screen);
        this.stateFlagList = SmmArrays.append(this.stateFlagList, flags);
        this.stateTriggerEventList = SmmArrays.append(this.stateTriggerEventList, new int[0]);
        this.stateOutTransList = SmmArrays.append(this.stateOutTransList, new int[0]);
        if (this.stateMediatorList != null) {
            this.stateMediatorList = SmmArrays.append(this.stateMediatorList, null);
        }
        if (this.stateSyncTriggerList != null) {
            this.stateSyncTriggerList = SmmArrays.append(this.stateSyncTriggerList, null);
        }
        if (this.stateSyncExitTriggerList != null) {
            this.stateSyncExitTriggerList = SmmArrays.append(this.stateSyncExitTriggerList, null);
        }
        if (this.stateSyncTargetList != null) {
            this.stateSyncTargetList = SmmArrays.append(this.stateSyncTargetList, null);
        }
        return id;
    }

    /* Each event of the state leads to the transition. The outer arrays are our copies by now (states appended first). */
    private void trigger(int state, int[] events, int trans) {
        int i = state - base();
        for (int e = 0; e < events.length; e++) {
            this.stateTriggerEventList[i] = SmmArrays.append(this.stateTriggerEventList[i], events[e]);
            this.stateOutTransList[i] = SmmArrays.append(this.stateOutTransList[i], trans);
        }
    }

    /* Appends a transition; returns its id. name null = no action logged. */
    private int addTransition(int flags, int target, int targetFlags, String name) {
        int id = base() + this.transFlagList.length;
        this.transFlagList = SmmArrays.append(this.transFlagList, flags);
        this.transTrgtStateList = SmmArrays.append(this.transTrgtStateList, new int[] {target});
        this.transTrgtFlagList = SmmArrays.append(this.transTrgtFlagList, new int[] {targetFlags});
        if (name != null) {
            this.names.put(new Integer(id), name);
        }
        return id;
    }

    public void execTransitionAction(SMServices services, int trans, int target) {
        String name = this.names == null ? null : (String)this.names.get(new Integer(trans));
        if (name == null) {
            super.execTransitionAction(services, trans, target);
            return;
        }
        Log.log("smm " + this.getTerminalName() + ": " + name + " (trans " + trans + ")");
        if (name.startsWith("open")) {
            services.addScreenAnimation(TerminalModeIds.ANIMATION_FORWARD);
        } else if (name.startsWith("back")) {
            services.addScreenAnimation(TerminalModeIds.ANIMATION_BACK);
        }
    }
}
