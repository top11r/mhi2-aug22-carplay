// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r. Cases follow luka-dev/mib2q-carplay-rgi's PDC tests
// (PdcResourcePolicyTest, PdcExternalEventsTest, OpsAudioDrawerTest, OpsStatusLineTest).
//
// Runs the hook's classes on the stock MU1438 classes: real parking components and
// their popup tables, real tracker, listener, drawer context and popup manager. Only
// the physical edges (HMI widgets, device, dispatcher, command execution) are faked.
//
//   PdcHostTest           every case must pass (hook classes first on the classpath)
//   PdcHostTest --stock   stock classes first: each bug the hook fixes must show
import com.mhi2.pdc.PdcSmallStageGuard;
import de.audi.app.earlyfunc.core.parking.IParkingSystem;
import de.audi.app.earlyfunc.core.parking.IParkingSystemController;
import de.audi.app.earlyfunc.core.parking.ParkingPartialPopupHandler;
import de.audi.app.earlyfunc.evo.parking.ParkingSystemControllerComponentEvo;
import de.audi.app.earlyfunc.evo.parking.ParkingSystemPLAComponentEvo;
import de.audi.app.earlyfunc.evo.parking.ParkingSystemVPSComponentEvo;
import de.audi.app.earlyfunc.evo.parking.ops.ParkingSystemOPSComponentEvo;
import de.audi.app.terminalmode.CommandListHelper;
import de.audi.app.terminalmode.ConvenientCommandList;
import de.audi.app.terminalmode.ExternalEventsListener;
import de.audi.app.terminalmode.IContext;
import de.audi.app.terminalmode.ITerminalLogger;
import de.audi.app.terminalmode.device.IActiveDeviceStateListener;
import de.audi.app.terminalmode.device.IDeviceManager;
import de.audi.app.terminalmode.device.TMDevice;
import de.audi.app.terminalmode.diagnosis.IDiagnosisManager;
import de.audi.app.terminalmode.interapp.HighPriorityResourceTracker;
import de.audi.app.terminalmode.keyevents.TMKeyEventsHandler;
import de.audi.app.terminalmode.osgi.IServiceManager;
import de.audi.app.terminalmode.statemachine.IStateHandler;
import de.audi.app.terminalmode.statemachine.TMState;
import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.hmi.HMIService;
import de.audi.atip.hmi.model.IntegerListCell;
import de.audi.atip.hmi.model.ListCell;
import de.audi.atip.hmi.modelaccess.ChoiceModelApp;
import de.audi.atip.hmi.modelaccess.ListModelApp;
import de.audi.atip.hmi.IRootWindow;
import de.audi.atip.hmi.view.IScreenManager;
import de.audi.atip.hmi.view.Screen;
import de.audi.atip.interapp.audio.drawer.AudioDrawerContext;
import de.audi.atip.log.LogChannel;
import de.audi.audio.AudioEnv;
import de.audi.audio.context.AudioDrawerContextImpl;
import de.audi.tghu.command.Command;
import de.audi.tghu.hmi.evo.IPartialPopupControllerEvo;
import de.esolutions.fw.util.commons.SimpleIntObjectMap;
import de.esolutions.fw.util.commons.job.DispatcherBase;
import de.esolutions.fw.util.commons.job.Job;
import de.esolutions.hmi.widgets.audi.base.AbstractScreenWidget;
import de.esolutions.hmi.widgets.audi.base.HMITerminalImpl;
import de.esolutions.hmi.widgets.audi.evo.high.PartialPopupManagerEvoHigh;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dsi.ifc.carparkingsystem.DisplayContent;
import sun.misc.Unsafe;

public final class PdcHostTest {
    static final int OPS = 2100008;
    static final LogChannel LOG = new LogChannel() {
        public void log(int a, String b, Object c, Object d, Object e, Object f, long g, long h, long i, int j, Throwable k) { }
        public void log(int a, int b, Object c, Object d, Object e, Object f, long g, long h, long i, int j, Throwable k) { }
    };
    static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe)f.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    interface Call { Object call(String name, Object[] args) throws Exception; }

    static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(PdcHostTest.class.getClassLoader(), new Class[]{type}, (p, m, a) -> {
            if (m.getName().equals("toString")) return type.getSimpleName() + " fixture";
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object r = call.call(m.getName(), a == null ? new Object[0] : a);
            if (r == null && m.getReturnType() == boolean.class) return false;
            if (r == null && m.getReturnType() == int.class) return 0;
            return r;
        }));
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static Field field(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError("missing field " + name);
    }

    static void put(Object target, String name, Object value) throws Exception {
        Field f = field(target.getClass(), name);
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(f), value);
    }

    static void putBool(Object target, String name, boolean value) {
        Field f = field(target.getClass(), name);
        UNSAFE.putBoolean(target, UNSAFE.objectFieldOffset(f), value);
    }

    static void putInt(Object target, String name, int value) {
        Field f = field(target.getClass(), name);
        UNSAFE.putInt(target, UNSAFE.objectFieldOffset(f), value);
    }

    @SuppressWarnings("unchecked")
    static <T> T alloc(Class<T> type) throws Exception {
        return (T)UNSAFE.allocateInstance(type);
    }

    /* ---------------------------------------------------------------- fixture */

    static final class Choice {
        int value, writes;
        final ChoiceModelApp model = proxy(ChoiceModelApp.class, (name, args) -> {
            if (name.equals("getValue")) return value;
            if (name.equals("setValue")) { value = (Integer)args[0]; writes++; }
            return null;
        });
    }

    public static class CarPlayDevice extends TMDevice {
        boolean carplay = true;
        public boolean isValid() { return true; }
        public boolean isCarplayDevice() { return carplay; }
    }

    public static class Keys extends TMKeyEventsHandler {
        int activated, deactivated;
        public Keys() { super(null, null); }
        public void activate() { activated++; }
        public void deactivate() { deactivated++; }
    }

    /* Runs nothing: records which commands TerminalMode would execute. */
    static final List<String> COMMANDS = new ArrayList<>();

    public static class Recorder extends ConvenientCommandList {
        public Recorder() { super(null); }
        public ConvenientCommandList addSingle(Command command) {
            COMMANDS.add(command.getClass().getName() + " " + commandName(command));
            return this;
        }
        public void execute(String source) { }
    }

    public static class Commands extends CommandListHelper {
        public Commands() { super(null); }
        public ConvenientCommandList create() {
            try {
                return alloc(Recorder.class);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    static String commandName(Object command) {
        for (Class<?> t = command.getClass(); t != null; t = t.getSuperclass()) {
            for (Field f : t.getDeclaredFields()) {
                if (f.getType() == String.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(command);
                        if (v != null) return v.toString();
                    } catch (Exception ignored) { }
                }
            }
        }
        return "";
    }

    static boolean commandIssued(String part) {
        for (String c : COMMANDS) if (c.contains(part)) return true;
        return false;
    }

    public static class Dispatcher extends DispatcherBase {
        Runnable pending;
        public Job execute(Object job, long delay) { pending = (Runnable)job; return null; }
        public Dispatcher() { super(null, null, null, null); }
        void fire() { Runnable r = pending; pending = null; if (r != null) r.run(); }
    }

    static final class Fixture {
        final AbstractScreenWidget carplay = new AbstractScreenWidget(3200000);
        Screen current = carplay, connected = carplay;
        final Map<Integer, Choice> choices = new HashMap<>();
        final TMState state = new TMState();
        final CarPlayDevice device = new CarPlayDevice();
        final Keys keys;
        final Commands commands;
        final Dispatcher dispatcher;
        final IScreenManager screenManager = proxy(IScreenManager.class, (name, args) -> {
            if (name.equals("getCurrentConnectedScreen")) return connected;
            if (name.equals("isScreenChangePending")) return false;
            if (name.equals("getPendingScreenId")) return -1;
            throw new AssertionError("unexpected screen manager call " + name);
        });
        final HMIService hmi = proxy(HMIService.class, (name, args) -> {
            if (name.equals("getRootWindow")) return proxy(IRootWindow.class, (n, a) -> current);
            if (name.equals("getScreenManager")) return screenManager;
            throw new AssertionError("unexpected HMI call " + name);
        });
        final IFrameworkAccess framework = proxy(IFrameworkAccess.class, (name, args) -> {
            if (name.equals("getHMIService")) return hmi;
            throw new AssertionError("unexpected framework call " + name);
        });
        final IContext context;
        final IStateHandler stateHandler;
        final HighPriorityResourceTracker tracker;
        IActiveDeviceStateListener deviceListener;

        Fixture() throws Exception {
            PdcSmallStageGuard.parkingStopped();
            COMMANDS.clear();
            keys = alloc(Keys.class);
            commands = alloc(Commands.class);
            dispatcher = alloc(Dispatcher.class);
            final IDeviceManager devices = proxy(IDeviceManager.class, (name, args) -> {
                if (name.equals("getActiveDevice")) return device;
                if (name.equals("addActiveDeviceListener")) deviceListener = (IActiveDeviceStateListener)args[0];
                return null;
            });
            final ITerminalLogger logger = proxy(ITerminalLogger.class, (name, args) -> LOG);
            context = proxy(IContext.class, (name, args) -> {
                switch (name) {
                    case "getDeviceManager": return devices;
                    case "getChoiceModel": return choice((Integer)args[0]).model;
                    case "getLogger": return logger;
                    case "getDispatcher": return dispatcher;
                    case "getKeyEventsHandler": return keys;
                    case "getCommandListHelper": return commands;
                    case "getFramework": return framework;
                    case "getEventBus": return null;
                    default: throw new AssertionError("unexpected context call " + name);
                }
            });
            stateHandler = proxy(IStateHandler.class, (name, args) -> {
                if (name.equals("getCurrentState")) return state;
                throw new AssertionError("unexpected state handler call " + name);
            });
            tracker = new HighPriorityResourceTracker(proxy(IServiceManager.class, (n, a) -> null), LOG, commands,
                context, proxy(IDiagnosisManager.class, (n, a) -> null), stateHandler);
            tracker.init(); // as TerminalModeApplication does; ours attaches CarPlaySession
        }

        Choice choice(int id) {
            Choice c = choices.get(id);
            if (c == null) choices.put(id, c = new Choice());
            return c;
        }

        Choice wizard() { return choice(3200046); }

        boolean screenTaken() { return commandIssued("Acquire SCREEN"); }

        /* Side OPS with an idle PLA, as stock assembles it; popup request confirmed. */
        void sideOps() throws Exception {
            DisplayContent content = new DisplayContent();
            content.popup = 1;
            PdcSmallStageGuard.parkingContentChanging(content, new StockParking().involved(content), hmi);
            PdcSmallStageGuard.popupVisible(OPS, 0);
        }
    }

    /* Real MU1438 components and the real controller's popup-to-system table. */
    static final class StockParking {
        final ParkingSystemControllerComponentEvo controller = alloc(ParkingSystemControllerComponentEvo.class);
        final ParkingSystemOPSComponentEvo ops = alloc(ParkingSystemOPSComponentEvo.class);
        final ParkingSystemPLAComponentEvo pla = alloc(ParkingSystemPLAComponentEvo.class);
        final ParkingSystemVPSComponentEvo vps = alloc(ParkingSystemVPSComponentEvo.class);

        StockParking() throws Exception {
            putInt(ops, "opsType", 2);
            SimpleIntObjectMap available = new SimpleIntObjectMap();
            Map<Integer, List<IParkingSystem>> mappings = new HashMap<>();
            for (IParkingSystem system : new IParkingSystem[]{ops, vps, pla}) {
                available.add(system.getParkingSystemID(), system);
                for (int popup : system.getSupportedDSIPopupIDs()) {
                    mappings.computeIfAbsent(popup, k -> new ArrayList<>()).add(system);
                }
            }
            put(controller, "availableParkingSystems", available);
            put(controller, "parkingSystemsForPopup", mappings);
        }

        List involved(DisplayContent content) {
            return controller.getInvolvedParkingSystems(content);
        }
    }

    /* ------------------------------------------------------------------ cases */

    /** Which parking contents may keep CarPlay: side OPS (with an idle PLA) only. */
    static void classification() throws Exception {
        StockParking stock = new StockParking();
        for (int popup : new int[]{1, 4, 7, 17}) {
            Fixture f = new Fixture();
            DisplayContent content = new DisplayContent();
            content.popup = popup;
            List systems = stock.involved(content);
            check(systems.size() == 2 && systems.contains(stock.ops) && systems.contains(stock.pla),
                "stock side OPS no longer comes with the idle PLA, popup=" + popup);
            PdcSmallStageGuard.parkingContentChanging(content, systems, f.hmi);
            f.tracker.processMsg(108);
            check(f.wizard().writes == 0 && !f.screenTaken() && f.carplay.getSmallStageType() == 6,
                "side OPS + idle PLA took CarPlay, popup=" + popup);
        }
        for (int popup : new int[]{0, 2, 3, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 99}) {
            Fixture f = new Fixture();
            DisplayContent content = new DisplayContent();
            content.popup = popup;
            PdcSmallStageGuard.parkingContentChanging(content, stock.involved(content), f.hmi);
            f.tracker.processMsg(108);
            check(f.wizard().writes == 1 && f.screenTaken() && f.carplay.getSmallStageType() == 1,
                "parking popup " + popup + " kept CarPlay instead of the stock takeover");
        }
        Fixture f = new Fixture();
        DisplayContent ara = new DisplayContent();
        ara.popup = 1;
        ara.mode = 12;
        PdcSmallStageGuard.parkingContentChanging(ara, stock.involved(ara), f.hmi);
        f.tracker.processMsg(108);
        check(f.screenTaken(), "trailer-assist mode kept CarPlay");
        putBool(stock.pla, "systemActive", true);
        f = new Fixture();
        DisplayContent plaContent = new DisplayContent();
        plaContent.popup = 4;
        PdcSmallStageGuard.parkingContentChanging(plaContent, stock.involved(plaContent), f.hmi);
        f.tracker.processMsg(108);
        check(f.screenTaken(), "active PLA classified as idle");
        putBool(stock.pla, "systemActive", false);
        System.out.println("classification: side OPS (+idle PLA) keeps CarPlay, everything else stock  PASS");
    }

    /** Message 108 for the side OPS beside CarPlay: no Main Wizard, no SCREEN. */
    static void tracker108() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        f.tracker.processMsg(108);
        check(f.wizard().writes == 0 && !f.screenTaken(), "pure OPS 108 toggled Main Wizard");
        f.tracker.processMsg(107);
        // a real camera afterwards is the stock takeover
        PdcSmallStageGuard.parkingStopped();
        COMMANDS.clear();
        f.tracker.processMsg(108);
        check(f.wizard().writes == 1 && f.screenTaken() && f.choice(3200049).value == 3,
            "camera 108 lost the stock takeover");
        // side sensors straight after the camera: the camera's hold on SCREEN goes
        f.sideOps();
        COMMANDS.clear();
        f.tracker.processMsg(108);
        check(commandIssued("Release SCREEN") && !f.screenTaken() && f.choice(3200049).value == 0
            && f.wizard().writes == 1, "side OPS after the camera left CarPlay blocked");
        System.out.println("tracker: OPS 108 keeps CarPlay, camera 108 takes over, OPS after camera releases  PASS");
    }

    static ExternalEventsListener listener(Fixture f) throws Exception {
        ExternalEventsListener l = new ExternalEventsListener(f.context, f.stateHandler);
        putBool(l, "screenActive", true);
        putBool(l, "bigStage", true);
        return l;
    }

    /** SMALL_STAGE beside the side OPS: CarPlay keeps the screen, input goes to the popup. */
    static void kombiSmallStage() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        ExternalEventsListener l = listener(f);
        l.updateKombiStage(false);
        check(!commandIssued("HMIDeactivated") && f.keys.deactivated == 1,
            "SMALL_STAGE beside OPS deactivated CarPlay");
        l.updateKombiStage(true);
        check(commandIssued("HMIActivated") && f.keys.activated == 1, "BIG_STAGE did not give CarPlay its input back");
        // HOME (CarPlay no longer the current screen) is stock
        f.current = new AbstractScreenWidget(100000);
        COMMANDS.clear();
        l.updateKombiStage(false);
        check(commandIssued("HMIDeactivated"), "SMALL_STAGE outside CarPlay skipped the stock deactivation");
        System.out.println("listener: SMALL_STAGE beside OPS keeps CarPlay, HOME stock  PASS");
    }

    /** AP 1002 queued while OPS was open, delivered after it closed. */
    static void staleActionProxy() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        ExternalEventsListener l = listener(f);
        l.actionProxyCallPerformed(1002, null);
        PdcSmallStageGuard.popupHidden(OPS, 0);
        check(f.carplay.getSmallStageType() == 1, "closing OPS did not restore the screen");
        f.dispatcher.fire();
        check(!commandIssued("HMIDeactivated"), "a 1002 from the closed OPS took the screen");
        // a takeover in between invalidates it
        f.sideOps();
        l.actionProxyCallPerformed(1002, null);
        PdcSmallStageGuard.reset();
        f.dispatcher.fire();
        check(commandIssued("HMIDeactivated"), "a 1002 after a takeover was suppressed");
        // and a 1002 with no parking at all is stock
        Fixture g = new Fixture();
        ExternalEventsListener m = listener(g);
        m.actionProxyCallPerformed(1002, null);
        g.dispatcher.fire();
        check(commandIssued("HMIDeactivated"), "a plain 1002 was suppressed");
        System.out.println("listener: queued 1002 checked against the parking state it arrived in  PASS");
    }

    /** "Check surroundings!" (APS column) hidden while protected, restored after. */
    static void apsDrawer() throws Exception {
        final ListCell[][] row = new ListCell[1][];
        ListModelApp model = proxy(ListModelApp.class, (name, args) -> {
            if (name.equals("addRow")) { row[0] = ((ListCell[])args[0]).clone(); return null; }
            if (name.equals("getCell")) return row[0][(Integer)args[1]];
            if (name.equals("setCell")) { row[0][(Integer)args[1]] = (ListCell)args[2]; return null; }
            return null;
        });
        Object hmiApp = proxy(Class.forName("de.audi.atip.hmi.IHMIServiceApp"), (name, args) -> {
            if (name.equals("getListModel")) return model;
            throw new AssertionError("unexpected HMI app call " + name);
        });
        IFrameworkAccess fw = proxy(IFrameworkAccess.class, (name, args) -> {
            if (name.equals("isFrontMU")) return true;
            if (name.equals("getHmiServiceApp") || name.equals("getHMIService")) return hmiApp;
            throw new AssertionError("unexpected audio framework call " + name);
        });
        AudioEnv env = alloc(AudioEnv.class);
        put(env, "framework", fw);
        put(env, "lcMain", LOG);
        AudioDrawerContextImpl drawer = new AudioDrawerContextImpl(env);
        Fixture f = new Fixture();
        f.sideOps();
        IntegerListCell warning = AudioDrawerContext.SOURCE_AUDIO_STATE_APS_ACTIVE_HIGH_PRIO.getCell();
        drawer.setContext(AudioDrawerContext.SOURCE_APS, AudioDrawerContext.SOURCE_AUDIO_STATE_APS_ACTIVE_HIGH_PRIO);
        check(row[0][AudioDrawerContext.PRIO_IDX_APS] == AudioDrawerContext.LIST_CELL_INACTIVE,
            "APS drawer shown over CarPlay");
        PdcSmallStageGuard.popupHidden(OPS, 0);
        PdcSmallStageGuard.parkingStopped();
        check(row[0][AudioDrawerContext.PRIO_IDX_APS] == warning, "APS request lost after the popup closed");
        System.out.println("drawer: APS warning hidden beside CarPlay, restored after  PASS");
    }

    /** Status line 62 (clock/LTE bar) stays hidden over CarPlay beside the popup. */
    static void statusLine() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        // the widget log channels are static and read the framework the HMI sets at start-up
        Field widgetFramework = field(Class.forName("de.esolutions.hmi.widgets.audi.base.AbstractWidget"), "framework");
        if (widgetFramework.get(null) == null) {
            widgetFramework.set(null, proxy(IFrameworkAccess.class, (name, args) -> {
                if (name.equals("getLogChannel")) return LOG;
                throw new AssertionError("unexpected widget framework call " + name);
            }));
        }
        PartialPopupManagerEvoHigh manager = alloc(PartialPopupManagerEvoHigh.class);
        HMITerminalImpl terminal = (HMITerminalImpl)alloc(Class.forName("de.esolutions.hmi.widgets.audi.evo.high.HMITerminalImplMIB2High"));
        putInt(terminal, "terminalID", 0);
        put(manager, "terminal", terminal);
        put(manager, "currentConnectedScreen", f.connected);
        IPartialPopupControllerEvo statusLine = proxy(IPartialPopupControllerEvo.class, (name, args) -> {
            if (name.equals("getID")) return 62;
            return null;
        });
        Method doShow = Class.forName("de.esolutions.hmi.widgets.audi.base.PartialPopupManager").getDeclaredMethod("doShowPopup", IPartialPopupControllerEvo.class, int.class);
        doShow.setAccessible(true);
        // The guarded path returns before touching the manager's (empty) internals;
        // a crash here is a test failure, not "shown".
        Object result = doShow.invoke(manager, statusLine, 0);
        check(Integer.valueOf(2).equals(result), "status line 62 shown over CarPlay (" + result + ")");
        System.out.println("popup manager: status line 62 hidden beside CarPlay  PASS");
    }

    /** Partial-popup callbacks for other popups or terminals don't cancel the OPS. */
    static void popupCallbacks() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        final int[] canceled = {0};
        ParkingPartialPopupHandler handler = new ParkingPartialPopupHandler(null,
            proxy(IParkingSystemController.class, (n, a) -> {
                if (n.equals("notifyPartialPopupCanceled")) canceled[0]++;
                return null;
            }), LOG);
        handler.partialPopupVisible(OPS, 0);
        handler.partialPopupListenerRegistered(2100380, 0, false);
        handler.partialPopupHidden(2100380, 0);
        handler.partialPopupHidden(OPS, 1);
        check(canceled[0] == 0 && handler.isPopupActive() && PdcSmallStageGuard.shouldKeepCarPlayScreen(),
            "an unrelated hidden callback canceled the OPS");
        handler.partialPopupHidden(OPS, 0);
        check(canceled[0] == 1 && !handler.isPopupActive() && !PdcSmallStageGuard.shouldKeepCarPlayScreen()
            && f.carplay.getSmallStageType() == 1, "closing the OPS did not clean up");
        System.out.println("popup handler: only the OPS's own hidden callback releases it  PASS");
    }

    /** Disconnect releases the guard and the drawer mask. */
    static void disconnect() throws Exception {
        Fixture f = new Fixture();
        f.sideOps();
        check(f.carplay.getSmallStageType() == 6, "guard not armed");
        check(f.deviceListener != null, "CarPlaySession did not listen for the active device");
        f.device.carplay = false;
        f.deviceListener.updateActiveDeviceState(f.device);
        check(f.carplay.getSmallStageType() == 1 && !PdcSmallStageGuard.shouldKeepCarPlayScreen(),
            "disconnect kept the guard");
        System.out.println("session: disconnect releases the guard  PASS");
    }

    interface Case { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--stock")) {
            expectBug("pure OPS 108 toggled Main Wizard", PdcHostTest::tracker108);
            expectBug("SMALL_STAGE beside OPS deactivated CarPlay", PdcHostTest::kombiSmallStage);
            expectBug("APS drawer shown over CarPlay", PdcHostTest::apsDrawer);
            // shortcut: stock shows 62 through the manager's queues, which this fixture leaves
            // empty, so it can't run; port luka's OpsStatusLineTest setup if this needs a control.
            System.out.println("stock: status line 62 - no control (needs a full popup manager)");
            return;
        }
        classification();
        tracker108();
        kombiSmallStage();
        staleActionProxy();
        apsDrawer();
        statusLine();
        popupCallbacks();
        disconnect();
        System.out.println("PdcHostTest: all passed");
    }

    static void expectBug(String bug, Case c) throws Exception {
        try {
            c.run();
        } catch (AssertionError e) {
            if (e.getMessage() != null && e.getMessage().startsWith(bug)) {
                System.out.println("stock: " + bug + "  (reproduced)");
                return;
            }
            throw e;
        }
        throw new AssertionError("stock classes passed, the test does not catch: " + bug);
    }
}
