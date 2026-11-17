// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package de.audi.tghu.terminalmode.hmi;

import com.mhi2.settings.mount.terminalmode.TerminalModeModelBankExt;
import com.mhi2.settings.mount.terminalmode.TerminalModeScreenFactoryExt;
import de.audi.atip.activator.AbstractHMIActivator;
import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.hmi.HMIConditionBank;
import de.audi.atip.hmi.HMIModelBank;
import de.audi.atip.hmi.view.AbstractScreenFactory;
import de.audi.atip.model.TerminalModeModelBank;
import de.audi.tghu.terminalmode.hmi.evohigh.TerminalModeConditionBank;
import de.audi.tghu.terminalmode.hmi.evohigh.TerminalModeScreenFactory;

/*
 * Replaces the stock Activator of the Audi smartphone interface HMI (module 32)
 * on the boot classpath. Same as stock except that the bank and the screen
 * factory are ours; either falls back to the stock one if it cannot be made.
 */
public class Activator extends AbstractHMIActivator {
    private TerminalModeScreenFactory instance = null;
    private TerminalModeConditionBank conditionBank;

    public Activator() {
        super(32, "TerminalMode", System.getProperty("variant.skin", "EvoHigh"), newBank());
    }

    private static HMIModelBank newBank() {
        try {
            return new TerminalModeModelBankExt();
        } catch (Throwable t) {
            return new TerminalModeModelBank();
        }
    }

    protected synchronized AbstractScreenFactory getScreenFactory() {
        if (this.instance == null) {
            IFrameworkAccess framework = this.getFramework();
            try {
                this.instance = new TerminalModeScreenFactoryExt(framework);
            } catch (Throwable t) {
                this.instance = new TerminalModeScreenFactory(framework);
            }
        }
        return this.instance;
    }

    public HMIConditionBank getConditionBank() {
        if (this.conditionBank == null) {
            this.conditionBank = new TerminalModeConditionBank((TerminalModeScreenFactory)this.getScreenFactory());
        }
        return this.conditionBank;
    }
}
