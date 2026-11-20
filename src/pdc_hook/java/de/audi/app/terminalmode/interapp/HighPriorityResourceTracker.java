// SPDX-License-Identifier: GPL-3.0-or-later
// Replacement of the stock MU1438 class of the same name. The stock parts belong
// to their owners; this licence covers the changes only.
// Changes: Copyright (c) 2026 LuKa (@LuKa_dev), luka-dev/mib2q-carplay-rgi@91e301df
// (java_patch/de/audi/app/terminalmode/interapp/HighPriorityResourceTracker.java);
// K3346 (MU1438) port: Copyright (C) 2026 top11r
package de.audi.app.terminalmode.interapp;

import com.mhi2.pdc.CarPlaySession;
import com.mhi2.pdc.Log;
import com.mhi2.pdc.PdcSmallStageGuard;
import de.audi.app.terminalmode.CommandListHelper;
import de.audi.app.terminalmode.IContext;
import de.audi.app.terminalmode.ITerminalModeComponent;
import de.audi.app.terminalmode.diagnosis.IDiagnosisCommandProvider;
import de.audi.app.terminalmode.diagnosis.IDiagnosisManager;
import de.audi.app.terminalmode.osgi.IServiceManager;
import de.audi.app.terminalmode.statemachine.IStateHandler;
import de.audi.app.terminalmode.statemachine.Resource;
import de.audi.app.terminalmode.statemachine.TMState;
import de.audi.app.terminalmode.statemachine.commands.AbstractCommand;
import de.audi.app.terminalmode.util.LockManager;
import de.audi.atip.hmi.modelaccess.ChoiceModelApp;
import de.audi.atip.interapp.bap.ecall.data.PhoneCall;
import de.audi.atip.interapp.phone.IEcallState;
import de.audi.atip.interapp.phone.ITelEcallStateListener;
import de.audi.atip.log.LogChannel;
import de.audi.atip.msg.MsgListener;
import de.audi.atip.power.DefaultPowerEventListener;
import de.audi.atip.power.PowerEventListener;
import de.audi.tghu.command.CommandList;

/** Stock MU1438 resource policy; parking message 108 for a standalone side
 * OPS beside CarPlay neither switches to the Main Wizard nor takes SCREEN. */
public class HighPriorityResourceTracker
    extends DefaultPowerEventListener
    implements ITerminalModeComponent, ITelEcallStateListener, MsgListener {
    private final LockManager.Lock ecallLock;
    private final LockManager.Lock powerManagerLock;
    private final LockManager.Lock rvcLock;
    /* LockManager.Lock has no getter, and release() always re-evaluates SCREEN. */
    private volatile boolean ecallHeld;
    private volatile boolean rvcHeld;
    private final IServiceManager serviceManager;
    private final CommandListHelper commandListHelper;
    private final IContext context;
    private final LogChannel lc;
    private final IDiagnosisManager diagnosisManager;
    private final IDiagnosisCommandProvider diagnosisCommandProvider;
    private final IStateHandler stateHandler;
    public static final int SCREEN_NOT_BLOCKED = 0;
    public static final int SCREEN_BLOCKED_CARPLAY_BY_ECALL = 1;
    public static final int SCREEN_BLOCKED_ANDROIDAUTO_BY_ECALL = 2;
    public static final int SCREEN_BLOCKED_CARPLAY_BY_RVC = 3;
    public static final int SCREEN_BLOCKED_ANDROIDAUTO_BY_RVC = 4;

    public HighPriorityResourceTracker(IServiceManager serviceManager, LogChannel logChannel,
        CommandListHelper commandListHelper, IContext context, IDiagnosisManager diagnosisManager,
        IStateHandler stateHandler) {
        this.serviceManager = serviceManager;
        this.commandListHelper = commandListHelper;
        this.lc = logChannel;
        this.context = context;
        this.diagnosisManager = diagnosisManager;
        this.stateHandler = stateHandler;
        LockManager lockManager = new LockManager(new LockStateChangedListener(), "HighPriorityResourceTracker", logChannel);
        this.ecallLock = lockManager.createLock("eCall");
        this.powerManagerLock = lockManager.createLock("powerManager");
        this.rvcLock = lockManager.createLock("rvcLock");
        this.diagnosisCommandProvider = new IDiagnosisCommandProvider() {
            public String[] getDiagKeys() {
                return new String[]{"HighPriorityResourceTracker enable RVC", "HighPriorityResourceTracker disable RVC",
                    "HighPriorityResourceTracker enable eCall", "HighPriorityResourceTracker disable eCall"};
            }

            public void executeDiagCommand(String key, String[] args) {
                if ("HighPriorityResourceTracker enable RVC".equals(key)) {
                    HighPriorityResourceTracker.this.parkingActivated();
                } else if ("HighPriorityResourceTracker disable RVC".equals(key)) {
                    HighPriorityResourceTracker.this.processMsg(107);
                } else if ("HighPriorityResourceTracker enable eCall".equals(key)) {
                    HighPriorityResourceTracker.this.updateEcallState(0, new DiagECallState(true));
                } else if ("HighPriorityResourceTracker disable eCall".equals(key)) {
                    HighPriorityResourceTracker.this.updateEcallState(0, new DiagECallState(false));
                }
            }
        };
    }

    public void init() {
        this.serviceManager.registerService(ITelEcallStateListener.class, this, IServiceManager.EMPTY_PARAMETERS);
        this.serviceManager.registerService(PowerEventListener.class, this, IServiceManager.EMPTY_PARAMETERS);
        this.serviceManager.registerService(MsgListener.class, this, IServiceManager.EMPTY_PARAMETERS);
        this.diagnosisManager.addCommandProvider(0, this.diagnosisCommandProvider);
        CarPlaySession.attach(this.context);
    }

    public void deinit() {
        CarPlaySession.detach(this.context);
    }

    public void updateEcallState(int terminal, IEcallState state) {
        this.ecallHeld = state.isServiceActive();
        this.ecallLock.setAcquired(state.isServiceActive());
        if (state.isServiceActive()) {
            this.acquireHighPriorityResources(Resource.AUDIO_MEDIA);
            this.context.getChoiceModel(3200049).setValue(this.context.getDeviceManager().getActiveDevice().isCarplayDevice() ? 1 : 2);
        } else {
            this.releaseHighPriorityResources(Resource.AUDIO_MEDIA);
            this.context.getChoiceModel(3200049).setValue(0);
        }
    }

    public void processMsg(int msg) {
        if (msg == 108) {
            if (this.context.getDeviceManager().getActiveDevice().isCarplayDevice()
                && PdcSmallStageGuard.shouldKeepCarPlayScreen()) {
                /* 108 means parking active, not necessarily the camera. The
                 * controller has classified the new component list already. */
                if (this.rvcHeld) {
                    this.rvcHeld = false;
                    this.rvcLock.release();
                    this.context.getChoiceModel(3200049).setValue(this.ecallHeld ? 1 : 0);
                }
                Log.i("PDC", "OPS 108: keep CarPlay SCREEN; no Main Wizard takeover");
                return;
            }
            Log.i("PDC", "parking 108: stock SCREEN/Main Wizard takeover");
            this.parkingActivated();
        } else if (msg == 107) {
            this.rvcHeld = false;
            this.rvcLock.release();
            this.context.getChoiceModel(3200049).setValue(0);
        }
    }

    private void parkingActivated() {
        PdcSmallStageGuard.parkingStopped();
        if (this.context.getDeviceManager().getActiveDevice().isCarplayDevice()) {
            ChoiceModelApp wizard = this.context.getChoiceModel(3200046);
            wizard.setValue(wizard.getValue() == 0 ? 1 : 0);
            this.context.getChoiceModel(3200049).setValue(this.context.getDeviceManager().getActiveDevice().isCarplayDevice() ? 3 : 4);
            this.rvcHeld = true;
            this.rvcLock.acquire();
        }
    }

    public void notifyPowerListenerOnEnterState(int state, int reason) {
        if (state == 0) {
            this.powerManagerLock.release();
        }
    }

    public void notifyPowerListenerOnExitState(int state, int reason) {
        if (state == 0) {
            this.powerManagerLock.acquire();
        }
    }

    private void acquireHighPriorityResources(final Resource resource) {
        this.commandListHelper.create().addSingle(new AbstractCommand(this.lc, "HighPriorityResourceTracker$Acquire " + resource, this.context) {
            public void execute() {
                TMState state = HighPriorityResourceTracker.this.stateHandler.getCurrentState();
                state.restrictAccessTo(resource);
                CommandList list = HighPriorityResourceTracker.this.stateHandler.changeState(state, 1, -1L);
                this.getCommandList().commandFinishedWithPostSequence(list);
            }
        }).execute("HighPriorityResourceTracker.acquireHighPriorityResources");
    }

    private void releaseHighPriorityResources(final Resource resource) {
        this.commandListHelper.create().addSingle(new AbstractCommand(this.lc, "HighPriorityResourceTracker$Release " + resource, this.context) {
            public void execute() {
                TMState state = HighPriorityResourceTracker.this.stateHandler.getCurrentState();
                state.allowAccessTo(resource);
                CommandList list = HighPriorityResourceTracker.this.stateHandler.changeState(state, 1, -1L);
                this.getCommandList().commandFinishedWithPostSequence(list);
            }
        }).execute("HighPriorityResourceTracker.releaseHiPrioResources");
    }

    private class DiagECallState implements IEcallState {
        private final boolean serviceActive;

        public DiagECallState(boolean serviceActive) {
            this.serviceActive = serviceActive;
        }

        public boolean hasActiveCall() {
            return false;
        }

        public boolean isServiceActive() {
            return this.serviceActive;
        }

        public int getServiceKind() {
            return 0;
        }

        public PhoneCall getCall() {
            return null;
        }

        public boolean isEmergencyCallType() {
            return false;
        }

        public boolean isCustomerCallNotAllowed() {
            return false;
        }

        public boolean isCustomerCallAllowed() {
            return false;
        }
    }

    private final class LockStateChangedListener implements LockManager.ILockStateChangedListener {
        public void onUnLocked() {
            HighPriorityResourceTracker.this.releaseHighPriorityResources(Resource.SCREEN);
        }

        public void onLocked(String held) {
            HighPriorityResourceTracker.this.acquireHighPriorityResources(Resource.SCREEN);
        }
    }
}
