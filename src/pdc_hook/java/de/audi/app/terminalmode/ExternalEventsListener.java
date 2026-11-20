// SPDX-License-Identifier: GPL-3.0-or-later
// Replacement of the stock MU1438 class of the same name. The stock parts belong
// to their owners; this licence covers the changes only.
// Changes: Copyright (c) 2026 LuKa (@LuKa_dev), luka-dev/mib2q-carplay-rgi@91e301df
// (java_patch/de/audi/app/terminalmode/ExternalEventsListener.java);
// K3346 (MU1438) port: Copyright (C) 2026 top11r
package de.audi.app.terminalmode;

import com.mhi2.pdc.CarPlaySession;
import com.mhi2.pdc.PdcSmallStageGuard;
import de.audi.app.terminalmode.actionproxy.IActionProxyListener;
import de.audi.app.terminalmode.audio.AudioConnectionState;
import de.audi.app.terminalmode.audio.IAudioStateListener;
import de.audi.app.terminalmode.commands.AudioFocusChanged;
import de.audi.app.terminalmode.commands.HMIActivated;
import de.audi.app.terminalmode.commands.HMIDeactivated;
import de.audi.app.terminalmode.commands.NavigationStateChanged;
import de.audi.app.terminalmode.commands.PhoneStateUpdate;
import de.audi.app.terminalmode.events.DefaultEventListener;
import de.audi.app.terminalmode.events.IEventBus;
import de.audi.app.terminalmode.statemachine.IStateHandler;
import de.audi.app.terminalmode.statemachine.Resource;
import de.audi.app.terminalmode.statemachine.commands.AbstractCommand;
import de.audi.atip.hmi.HMIService;
import de.audi.atip.interapp.phone.ITelState;
import de.audi.atip.interapp.phone.ITelStateListener;
import de.audi.atip.log.LogChannel;
import de.audi.tghu.command.CommandList;
import de.esolutions.fw.util.commons.job.DispatcherBase;
import java.util.Hashtable;
import java.util.Map;
import org.osgi.framework.ServiceRegistration;

/**
 * Stock MU1438 listener with luka-dev's partial-OPS exception: only CarPlay +
 * the guarded side OPS popup 2100008 skip HMIDeactivated on SMALL_STAGE / AP
 * 1002. A 1002 is checked against the parking state captured when it arrived,
 * so one that is delivered after OPS closed does not take the screen. HOME,
 * other HMI components, full-screen camera and disconnect stay stock.
 */
public class ExternalEventsListener
    extends DefaultEventListener
    implements IActionProxyListener, IAudioStateListener, INavigationStateListener, ITerminalModeComponent {
    private static final int ACTIONPROXY_DEBOUNCE_TIME = 70;
    private static final String LOGCLASS = "ExternalEventsListener";
    private final LogChannel logger;
    private final IContext context;
    private volatile ServiceRegistration phoneServiceRegistration;
    private final IStateHandler stateHandler;
    private final IEventBus eventBus;
    private volatile boolean bigStage;
    private volatile boolean screenActive;
    private final Debouncer apdebouncer;
    /* Parking state at the latest AP 1002; the debouncer delivers only the last AP. */
    private volatile PdcSmallStageGuard.ParkingTransition pendingParking;

    public ExternalEventsListener(IContext context, IStateHandler stateHandler) {
        this.apdebouncer = new Debouncer(ACTIONPROXY_DEBOUNCE_TIME, context.getDispatcher());
        this.logger = context.getLogger().main();
        this.context = context;
        this.stateHandler = stateHandler;
        this.eventBus = context.getEventBus();
    }

    public void init() {
        this.logger.log(100000000, "[%1.init]", LOGCLASS);
        this.bigStage = true;
        this.screenActive = false;
        this.context.addActionProxyListener(1001, this);
        this.context.addActionProxyListener(1002, this);
        this.eventBus.registerListener(this);
        this.context.getChoiceModel(3200035).setValue(78);
        this.phoneServiceRegistration = this.context.getServiceManager().registerService(ITelStateListener.class, new ITelStateListener() {
            public void updateTelState(int terminal, ITelState state) {
                ExternalEventsListener.this.logger.log(1000000, "<- [%1.updateTelState]", LOGCLASS);
                ExternalEventsListener.this.context.getCommandListHelper().create()
                    .addSingle(new PhoneStateUpdate(ExternalEventsListener.this.context, state.getCallActive(), ExternalEventsListener.this.stateHandler))
                    .execute("ExternalEventsListener.updateTelState");
            }
        }, new Hashtable(0));
        this.context.getNaviAppHandler().setNavigationStateListener(this);
        this.context.getAudioManager().addAudioContextListener(this);
    }

    public void deinit() {
        this.logger.log(1000000, "[%1.deinit]", LOGCLASS);
        PdcSmallStageGuard.reset();
        this.context.removeActionProxyListener(this);
        this.eventBus.unregisterListener(this);
        if (null != this.phoneServiceRegistration) {
            this.context.getServiceManager().unregisterService(this.phoneServiceRegistration);
        }
        this.context.getNaviAppHandler().setNavigationStateListener(null);
    }

    public void actionProxyCallPerformed(int id, Map parameters) {
        this.logger.log(1000000, "<<- [%1.actionProxyCallPerformed] pMethodID: %2", LOGCLASS, (long)id);
        if (id == 1002) {
            this.pendingParking = PdcSmallStageGuard.captureHmiDeactivation();
        } else if (id == 1001) {
            this.pendingParking = null;
        }
        this.apdebouncer.actionProxyCalled(id, parameters);
    }

    public void actionProxyCallDebounced(int id, Map parameters) {
        switch (id) {
            case 1001: {
                this.logger.log(1000000, "<<- [%1.actionProxyCallDebounced] AP_METHOD_HMI_ACTIVATED", LOGCLASS);
                if (this.carPlayOwnsScreen()) {
                    PdcSmallStageGuard.carPlayScreenActivated(this.hmiService());
                }
                if (this.bigStage) {
                    this.hmiActivated();
                } else if (this.carPlayOwnsScreen() && PdcSmallStageGuard.shouldKeepCarPlayScreen()) {
                    /* Entering CarPlay while OPS is already small still needs
                     * SCREEN. Input stays with the parking popup. */
                    this.context.getKeyEventsHandler().deactivate();
                    this.activateScreen();
                }
                this.screenActive = true;
                break;
            }
            case 1002: {
                this.logger.log(1000000, "<<- [%1.actionProxyCallDebounced] AP_METHOD_HMI_DEACTIVATED", LOGCLASS);
                PdcSmallStageGuard.ParkingTransition parking = this.pendingParking;
                this.pendingParking = null;
                if (this.screenActive && this.carPlayOwnsScreen()
                    && PdcSmallStageGuard.isParkingHmiDeactivation(parking)) {
                    /* The parking SMALL_STAGE can also publish AP 1002. */
                    this.logger.log(1000000, "[%1.actionProxyCallDebounced] partial OPS: suppress HMI_DEACTIVATED", LOGCLASS);
                    com.mhi2.pdc.Log.i("PDC", "partial OPS: suppress HMI_DEACTIVATED");
                    /* If OPS closed while this AP was debouncing, BIG_STAGE
                     * restored input already; don't turn it off again. */
                    if (!this.bigStage || PdcSmallStageGuard.shouldKeepCarPlayScreen()) {
                        this.context.getKeyEventsHandler().deactivate();
                    }
                } else {
                    this.hmiDeactivated();
                    this.screenActive = false;
                }
                break;
            }
        }
    }

    private boolean carPlayOwnsScreen() {
        return CarPlaySession.isActive() && !this.stateHandler.getCurrentState().isAccessRestricted(Resource.SCREEN);
    }

    private HMIService hmiService() {
        return this.context.getFramework().getHMIService();
    }

    private void hmiDeactivated() {
        /* SMALL_STAGE can precede AP 1001 when returning to CarPlay with OPS
         * already open: keep that guard, revoke it for a real takeover. */
        if (!this.carPlayOwnsScreen() || !PdcSmallStageGuard.shouldKeepCarPlayScreen()) {
            PdcSmallStageGuard.reset();
        }
        this.context.getKeyEventsHandler().deactivate();
        this.context.getCommandListHelper().create().addSingle(new HMIDeactivated(this.context, this.stateHandler)).execute("ExternalEventsListener.AP_METHOD_HMI_DEACTIVATED");
    }

    private void hmiActivated() {
        this.context.getKeyEventsHandler().activate();
        this.activateScreen();
    }

    private void activateScreen() {
        this.context.getCommandListHelper().create().addSingle(new HMIActivated(this.context, this.stateHandler)).execute("ExternalEventsListener.AP_METHOD_HMI_ACTIVATED");
    }

    public void audioStateChanged(AudioConnectionState state) {
        this.logger.log(100000000, "[%1.audioStateChanged] '%2'", LOGCLASS, state);
    }

    public void audioFocusChanged(boolean hasFocus) {
        this.logger.log(1000000, "<- [%1.audioFocusChanged] %2 %3", LOGCLASS, String.valueOf(hasFocus), this.context.getSmartphoneDSIManager().getSmartphoneType());
        this.context.getChoiceModel(4451).setValue(hasFocus ? (this.context.getSmartphoneDSIManager().getSmartphoneType().is(SmartphoneManager.SmartphoneType.CARPLAY) ? 1 : 2) : 0);
        AbstractCommand command = this.getCurrentCommand();
        if (null == command || !command.updateAudioFocus(hasFocus)) {
            this.context.getCommandListHelper().create().addSingle(new AudioFocusChanged(this.context, hasFocus, this.stateHandler)).execute("ExternalEventsListener.audioFocusChanged]");
        }
    }

    public void stateChanged(boolean active) {
        this.logger.log(1000000, "<- [%1.stateChanged]", LOGCLASS);
        this.context.getCommandListHelper().create().addSingle(new NavigationStateChanged(this.context, active, this.stateHandler)).execute("ExternalEventsListener.stateChanged]");
    }

    private AbstractCommand getCurrentCommand() {
        AbstractCommand command = null;
        CommandList list = this.context.getCommandListManager().getActiveCommandList();
        if (null != list) {
            command = (AbstractCommand)list.getActiveCommand();
        }
        return command;
    }

    public void updateKombiStage(boolean isBigStage) {
        if (isBigStage && this.screenActive) {
            this.hmiActivated();
        } else if (!isBigStage && this.screenActive && this.carPlayOwnsScreen()
            && PdcSmallStageGuard.shouldKeepCarPlayScreen()) {
            /* Keep SCREEN with TerminalMode so the right OPS panel overlays
             * CarPlay; input returns on BIG_STAGE. */
            this.logger.log(1000000, "[%1.updateKombiStage] SMALL_STAGE pure OPS: keep CarPlay SCREEN owner", LOGCLASS);
            com.mhi2.pdc.Log.i("PDC", "SMALL_STAGE pure OPS: keep CarPlay SCREEN owner");
            this.context.getKeyEventsHandler().deactivate();
        } else {
            this.hmiDeactivated();
        }
        this.bigStage = isBigStage;
    }

    private class Debouncer {
        final Object mutex = new Object();
        private int lastActionProxy = -1;
        private Map parameters = null;
        private DispatcherBase dispatcher;
        private int debounceTime;
        private volatile Runnable runner = null;

        public Debouncer(int debounceTime, DispatcherBase dispatcher) {
            this.debounceTime = debounceTime;
            this.dispatcher = dispatcher;
        }

        public void actionProxyCalled(int id, Map map) {
            synchronized (this.mutex) {
                this.lastActionProxy = id;
                this.parameters = map;
                ExternalEventsListener.this.logger.log(1000000, "<<- [%1.actionProxyCalled]", LOGCLASS);
                if (this.runner == null) {
                    ExternalEventsListener.this.logger.log(1000000, "<<- [%1.actionProxyCalled] create runner.", LOGCLASS);
                    this.runner = new Runnable() {
                        public void run() {
                            synchronized (Debouncer.this.mutex) {
                                ExternalEventsListener.this.logger.log(1000000, "<<- [%1.actionProxyCalled] Runner: delayed run", LOGCLASS);
                                ExternalEventsListener.this.actionProxyCallDebounced(Debouncer.this.lastActionProxy, Debouncer.this.parameters);
                                Debouncer.this.lastActionProxy = -1;
                                Debouncer.this.parameters = null;
                                Debouncer.this.runner = null;
                            }
                        }
                    };
                    this.dispatcher.execute(this.runner, (long)this.debounceTime);
                }
            }
        }
    }
}
