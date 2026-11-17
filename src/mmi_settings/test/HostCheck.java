// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r

import com.mhi2.settings.Settings;
import com.mhi2.settings.mount.terminalmode.TerminalModeIds;
import com.mhi2.settings.mount.terminalmode.TerminalModeModelBankExt;
import com.mhi2.settings.mount.terminalmode.TerminalModeSMMExt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

/*
 * Host check against the stock HMI classes (run by build.sh): the extended
 * state machine is built by running its init() on the stock arrays (no
 * framework needed), and every state and transition we add must be wired to
 * ids that exist. Also the model bank and the settings table.
 * Java 8 on the host; the head unit never loads this class.
 */
public class HostCheck {
    private static int failures;

    public static void main(String[] args) throws Exception {
        Object tm = smm(TerminalModeSMMExt.class);
        wired(tm, 32, 3200013);

        HashSet events = new HashSet();
        int[][] tmTriggers = (int[][])get(tm, "stateTriggerEventList");
        for (int i = 0; i < tmTriggers.length; i++) {
            for (int j = 0; tmTriggers[i] != null && j < tmTriggers[i].length; j++) {
                events.add(new Integer(tmTriggers[i][j]));
            }
        }
        for (int i = 0; i < TerminalModeIds.EVENTS_OPEN.length; i++) {
            check(events.contains(new Integer(TerminalModeIds.EVENTS_OPEN[i])), "event " + TerminalModeIds.EVENTS_OPEN[i] + " is a trigger");
        }

        Object stock = new de.audi.atip.model.TerminalModeModelBank();
        Object ours = new TerminalModeModelBankExt();
        Field models = de.audi.atip.hmi.model.AbstractModelBank.class.getDeclaredField("models");
        models.setAccessible(true);
        int stockModels = ((Object[])models.get(stock)).length;
        check(stockModels == TerminalModeIds.FIRST_MODEL % 100000, "stock bank ends where ours starts (" + stockModels + ")");
        check(((Object[])models.get(ours)).length == TerminalModeIds.MODEL_RESTART % 100000 + 1, "bank holds every setting and the restart model");

        HashSet flags = new HashSet();
        Method text = TerminalModeIds.class.getDeclaredMethod("text", new Class[] {int.class});
        text.setAccessible(true);
        for (int i = 0; i < Settings.ALL.length; i++) {
            Settings s = Settings.ALL[i];
            check(flags.add(s.flag), "flag " + s.flag + " is unique");
            check(s.ticked(s.present(true)) && !s.ticked(s.present(false)), s.flag + ": ticked/present map back");
            String row = (String)text.invoke(null, new Object[] {new Integer(TerminalModeIds.FIRST_TEXT_SETTING + i)});
            check(row != null, s.flag + " has a label in en.json");
        }
        int[] fixed = {TerminalModeIds.TEXT_ENTRY_ROW, TerminalModeIds.TEXT_RESTART,
            TerminalModeIds.TEXTS_NOTE[0], TerminalModeIds.TEXTS_NOTE[1], TerminalModeIds.TEXTS_NOTE[2], TerminalModeIds.TEXT_SCREENS[0],
            TerminalModeIds.TEXT_SCREENS[1], TerminalModeIds.TEXT_SCREENS[2]};
        for (int i = 0; i < fixed.length; i++) {
            check(text.invoke(null, new Object[] {new Integer(fixed[i])}) != null, "text " + fixed[i]);
        }

        System.out.println("host check: " + (failures == 0 ? "ok" : failures + " failed"));
        System.exit(failures == 0 ? 0 : 1);
    }

    /* Every trigger of the states from `first` on leads to a transition we added, whose targets are states or labels. */
    private static void wired(Object smm, int module, int first) throws Exception {
        int base = module * 100000;
        int[][] triggers = (int[][])get(smm, "stateTriggerEventList");
        int[][] outs = (int[][])get(smm, "stateOutTransList");
        int[] transFlags = (int[])get(smm, "transFlagList");
        int[][] targets = (int[][])get(smm, "transTrgtStateList");
        int states = ((int[])get(smm, "stateFlagList")).length;
        check(((int[])get(smm, "stateSuperstateList")).length == states && ((int[])get(smm, "stateDHSList")).length == states
            && triggers.length == states && outs.length == states, "module " + module + ": state arrays have one length");
        check(targets.length == transFlags.length, "module " + module + ": transition arrays have one length");
        check(states > first - base, "module " + module + ": our states were added");
        for (int i = first - base; i < states; i++) {
            check(triggers[i].length == outs[i].length, "state " + (base + i) + ": a transition per trigger");
            for (int j = 0; j < outs[i].length; j++) {
                int t = outs[i][j] - base;
                check(t >= 0 && t < transFlags.length, "state " + (base + i) + ": transition " + outs[i][j] + " exists");
                if (t < 0 || t >= transFlags.length) {
                    continue;
                }
                for (int k = 0; k < targets[t].length; k++) {
                    int target = targets[t][k];
                    boolean ok = target == -10 || (target < 0 && (transFlags[t] & 1) == 1)
                        || (target >= base && target < base + states);
                    check(ok, "transition " + outs[i][j] + ": target " + target);
                }
            }
        }
    }

    /* The state machine's init() on the stock arrays, without its constructor (that needs the HMI framework). */
    private static Object smm(Class cls) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object smm = ((sun.misc.Unsafe)f.get(null)).allocateInstance(cls);
        Method init = cls.getDeclaredMethod("init", new Class[0]);
        init.setAccessible(true);
        init.invoke(smm, new Object[0]);
        return smm;
    }

    private static Object get(Object o, String name) throws Exception {
        for (Class c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                /* next superclass */
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
