// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.pdc;

import de.audi.app.terminalmode.IContext;
import de.audi.app.terminalmode.device.IActiveDeviceStateListener;
import de.audi.app.terminalmode.device.TMDevice;

/* Whether CarPlay is the active TerminalMode device. Stands in for the CarPlay
 * lifecycle of luka-dev's CarPlayApp, which this build does not have: the
 * guard asks on every event and is released when the active device changes. */
public final class CarPlaySession {
    private static volatile IContext context;
    private static final IActiveDeviceStateListener LISTENER = new IActiveDeviceStateListener() {
        public void updateActiveDeviceState(TMDevice device) {
            if (!isCarPlay(device)) PdcSmallStageGuard.carPlayDisconnected();
        }
    };

    private CarPlaySession() {
    }

    /* Called from HighPriorityResourceTracker.init(), once per TerminalMode start. */
    public static void attach(IContext ctx) {
        if (ctx == null || ctx == context) return;
        context = ctx;
        try {
            ctx.getDeviceManager().addActiveDeviceListener(LISTENER);
        } catch (Throwable t) {
            Log.w("PDC", "cannot listen for the active device: " + t);
        }
    }

    public static void detach(IContext ctx) {
        if (ctx == null || ctx != context) return;
        context = null;
        try {
            ctx.getDeviceManager().removeActiveDeviceListener(LISTENER);
        } catch (Throwable t) {
            /* TerminalMode is going down anyway */
        }
        PdcSmallStageGuard.reset();
    }

    public static boolean isActive() {
        IContext ctx = context;
        if (ctx == null) return false;
        try {
            return isCarPlay(ctx.getDeviceManager().getActiveDevice());
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isCarPlay(TMDevice device) {
        return device != null && device.isValid() && device.isCarplayDevice();
    }
}
