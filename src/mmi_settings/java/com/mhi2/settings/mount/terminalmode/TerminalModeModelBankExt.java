// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount.terminalmode;

import com.mhi2.settings.Log;
import com.mhi2.settings.Settings;
import com.mhi2.settings.store.Commands;
import com.mhi2.settings.store.FlagStore;
import de.audi.atip.hmi.model.ChoiceModel;
import de.audi.atip.hmi.model.DefaultOptionListener;
import de.audi.atip.hmi.model.HMIModel;
import de.audi.atip.hmi.model.OptionModel;
import de.audi.atip.hmi.model.listener.DefaultChoiceListener;
import de.audi.atip.model.TerminalModeModelBank;

/*
 * The smartphone interface's model bank plus one checkbox ChoiceModel (value
 * 0/1, as the stock checkboxes) per Settings.ALL entry, and the model of the
 * "Restart CarPlay" action row. A checkbox shows its flag file; a change
 * writes the file (FlagStore) and the checkbox is set from the file again.
 * Settings that need an MMI restart raise that note once saved; a change that
 * could not be saved raises the "not saved" note instead.
 */
public class TerminalModeModelBankExt extends TerminalModeModelBank {
    private static final int FIRST = TerminalModeIds.FIRST_MODEL % 100000;
    private static final int RESTART = TerminalModeIds.MODEL_RESTART % 100000;
    /* mm-ipod (#79: it can deadlock on the iAP2 driver mutex), then smartphone_integrator: after a failed
     * session it answers every plug with "start_stack none"; the service manager starts it again at once
     * (car 2026-11-17). usblauncher starts a new mm-ipod on the next plug. */
    private static final String[][] RESTART_CARPLAY = {
        {"/bin/slay", "-f", "-s", "KILL", "mm-ipod"},
        {"/bin/slay", "-f", "-s", "KILL", "smartphone_integrator"},
    };

    public TerminalModeModelBankExt() {
        /* our indices must be past the stock bank; otherwise the Activator falls back to the stock bank */
        if (this.models.length > FIRST) {
            throw new IllegalStateException("stock bank has " + this.models.length + " models, ours start at " + FIRST);
        }
        for (int i = 0; i < this.modelIDs.length; i++) {
            if (this.modelIDs[i] >= TerminalModeIds.FIRST_MODEL && this.modelIDs[i] <= TerminalModeIds.MODEL_RESTART) {
                throw new IllegalStateException("stock bank already has model " + this.modelIDs[i]);
            }
        }
        if (this.models.length <= RESTART) {
            HMIModel[] grown = new HMIModel[RESTART + 1];
            System.arraycopy(this.models, 0, grown, 0, this.models.length);
            this.models = grown;
        }
        int ours = RESTART - FIRST + 1;
        int[] ids = new int[this.modelIDs.length + ours];
        System.arraycopy(this.modelIDs, 0, ids, 0, this.modelIDs.length);
        for (int i = 0; i < ours; i++) {
            ids[this.modelIDs.length + i] = TerminalModeIds.FIRST_MODEL + i;
        }
        this.modelIDs = ids;
    }

    protected synchronized void createModel(int index) {
        if (index < FIRST || index > RESTART) {
            super.createModel(index);
            return;
        }
        if (this.models[index] == null) {
            this.models[index] = index == RESTART ? (HMIModel)restartModel() : (HMIModel)settingModel(Settings.ALL[index - FIRST], index);
        }
    }

    private static ChoiceModel settingModel(final Settings setting, int index) {
        final ChoiceModel model = new ChoiceModel(100000 * TerminalModeIds.MODULE + index);
        model.setChoiceListener(new DefaultChoiceListener() {
            public void itemSelected(int id, int value, int unused, final int terminal) {
                boolean changed = model.getValue() != value;
                model.setValue(value);
                Log.log("bank: " + setting.flag + " row = " + value + " (terminal " + terminal + ")");
                if (!changed) {
                    return;
                }
                final boolean wanted = setting.present(value == 1);
                FlagStore.set(setting.flag, wanted, new FlagStore.Done() {
                    public void written(boolean present) {
                        model.setValue(setting.ticked(present) ? 1 : 0);
                        /* one note: the failure, or when a saved change takes effect */
                        if (present != wanted) {
                            showNote(terminal, TerminalModeIds.POPUPS_NOTE[TerminalModeIds.NOT_SAVED]);
                        } else if (setting.effect == Settings.MMI_RESTART) {
                            showNote(terminal, TerminalModeIds.POPUPS_NOTE[TerminalModeIds.MMI_RESTART_NOTE]);
                        }
                    }
                });
            }
        });
        model.setValue(setting.ticked(FlagStore.isPresent(setting.flag)) ? 1 : 0);
        return model;
    }

    /* Bound to the action row as stock binds its action rows (Connection manager's drawer, model 2500340):
     * an OptionModel, whose listener's keyTyped is the press. */
    private static OptionModel restartModel() {
        OptionModel model = new OptionModel(TerminalModeIds.MODEL_RESTART);
        model.setListener(new DefaultOptionListener() {
            public void keyTyped(int id, int targetModel, int targetRow, int unused, int terminal) {
                Log.log("bank: restart CarPlay (row " + targetRow + ", terminal " + terminal + ")");
                showNote(terminal, TerminalModeIds.POPUPS_NOTE[TerminalModeIds.RESTARTED]);
                Commands.submit(new Runnable() {
                    public void run() {
                        for (int i = 0; i < RESTART_CARPLAY.length; i++) {
                            Commands.run(RESTART_CARPLAY[i]);
                        }
                    }
                });
            }
        });
        return model;
    }

    private static void showNote(int terminal, int popup) {
        try {
            TerminalModeIds.framework.getHmiServiceApp().showPartialPopup(terminal, popup);
            Log.log("bank: note popup " + popup + " shown");
        } catch (Throwable t) {
            Log.log("bank: note popup failed: " + t);
        }
    }
}
