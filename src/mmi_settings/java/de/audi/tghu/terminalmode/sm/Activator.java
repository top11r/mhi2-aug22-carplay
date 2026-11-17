// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package de.audi.tghu.terminalmode.sm;

import com.mhi2.settings.mount.terminalmode.TerminalModeSMMExt;
import de.audi.atip.activator.AbstractSMMActivator;
import de.audi.atip.base.IFrameworkAccess;

/*
 * Replaces the stock Activator of the Audi smartphone interface state machine
 * (module 32) on the boot classpath. Same terminals as stock; each state
 * machine is ours (TerminalModeSMMExt keeps the stock arrays if it cannot extend them).
 */
public class Activator extends AbstractSMMActivator {
    public void init() {
        this.smmList = new TerminalModeSMM[8];
        if (this.framework.isFrontMU()) {
            this.smmList[0] = newSmm(this.framework, 0, "main");
            if (this.framework.isDualView()) {
                this.smmList[2] = newSmm(this.framework, 2, "frontPassenger");
            }
        } else {
            this.smmList[3] = newSmm(this.framework, 3, "rearSeatLeft");
            this.smmList[4] = newSmm(this.framework, 4, "rearSeatRight");
        }
        this.reqActionProxies = TerminalModeSMMActions.REQUIRED_ACTION_PROXY_MODULE_IDS;
    }

    private static TerminalModeSMM newSmm(IFrameworkAccess framework, int terminal, String name) {
        try {
            return new TerminalModeSMMExt(framework, terminal, name);
        } catch (Throwable t) {
            return new TerminalModeSMM(framework, terminal, name);
        }
    }
}
