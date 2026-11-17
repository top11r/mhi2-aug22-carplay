// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount.terminalmode;

import com.mhi2.settings.Log;
import com.mhi2.settings.Settings;
import com.mhi2.settings.mount.HmiLanguage;
import com.mhi2.settings.store.FlagStore;
import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.hmi.view.AbstractScreenFactory;
import de.audi.atip.hmi.view.HMIView;
import de.audi.atip.hmi.view.IPartialPopupController;
import de.audi.atip.hmi.view.Screen;
import de.audi.tghu.terminalmode.hmi.evohigh.FontFactory;
import de.audi.tghu.terminalmode.hmi.evohigh.TerminalModeScreenFactory;
import de.esolutions.hmi.widgets.audi.base.AbstractScreenWidget;
import de.esolutions.hmi.widgets.audi.base.AbstractWidget;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont;
import de.esolutions.hmi.widgets.audi.base.widgets.AbstractWidgetController;
import de.esolutions.hmi.widgets.audi.base.widgets.IRenderer;
import de.esolutions.hmi.widgets.audi.base.widgets.ScreenWidgetRenderer;
import de.esolutions.hmi.widgets.audi.evo.InstructionTextContoller;
import de.esolutions.hmi.widgets.audi.evo.ScreenWidgetEVO;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.CompositeRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.ContainerRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.FocusCursorRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.GlassplateRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.IconRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.LabelRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.MultiLineLabelRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.PartialPopupGlassplateRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.PartialPopupRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.ScreenRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.ScrollbarRendererKanziHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.TitleBarRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.menu.MenuRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.widgets.CompositeRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.ContainerController;
import de.esolutions.hmi.widgets.audi.evo.widgets.CursorController;
import de.esolutions.hmi.widgets.audi.evo.widgets.CursorRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.FocusCursorController;
import de.esolutions.hmi.widgets.audi.evo.widgets.GlassplateController;
import de.esolutions.hmi.widgets.audi.evo.widgets.IconController;
import de.esolutions.hmi.widgets.audi.evo.widgets.IconRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.LabelController;
import de.esolutions.hmi.widgets.audi.evo.widgets.LabelRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.PartialPopupController;
import de.esolutions.hmi.widgets.audi.evo.widgets.PartialPopupGlassplateController;
import de.esolutions.hmi.widgets.audi.evo.widgets.PartialPopupRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.PartialPopupStub;
import de.esolutions.hmi.widgets.audi.evo.widgets.ScrollbarController;
import de.esolutions.hmi.widgets.audi.evo.widgets.ScrollbarRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.StatusBarStubController;
import de.esolutions.hmi.widgets.audi.evo.widgets.TitleBarWidget;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.IInfoLineRenderer;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.InfoLineRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemController;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * The smartphone interface's screen factory plus:
 *  - our row "Switch to CarPlay settings" on the stock no-device screen, right
 *    after "Switch to connection manager", built like that row;
 *  - our "CarPlay settings" screen: the no-device screen's frame (title bar,
 *    status bar, fonts, colours) with a plain settings menu like Bluetooth
 *    settings: its checkboxes, "Route guidance ›" and "Virtual cockpit map ›" when
 *    those features are installed, and "Restart CarPlay" last;
 *  - the "Route guidance" and "Virtual cockpit map" screens those rows open;
 *  - rows of Settings.ALL;
 *  - the "takes effect later" note popup, like the stock "Bluetooth activated" one;
 *  - our texts.
 * A failure on the no-device screen leaves it stock.
 */
public class TerminalModeScreenFactoryExt extends TerminalModeScreenFactory {
    /* stock texts used in our title bar */
    private static final int TEXT_BREADCRUMB = 3200070;   /* "Audi smartphone interface" */
    private static final int TEXT_MENU_DISABLED = 3200072;
    private static final int[][] PALETTES = {
        {-1, -16777216, -1, -1, -6645094, -5592406, -4868683, -6645094, -1},
        {-1, -16777216, -65511, -5546136, -6645094, -5592406, -4868683, -6645094, -4703671},
        {-1, -16777216, -19456, -6123155, -6645094, -5592406, -4868683, -6645094, -6123155},
        {-1, -16777216, -14775126, -9594974, -6645094, -5592406, -4868683, -6645094, -9594974},
        {-1, -16777216, -10180096, -9200779, -6645094, -5592406, -4868683, -6645094, -9200779}};

    public TerminalModeScreenFactoryExt(IFrameworkAccess framework) {
        super(framework);
        TerminalModeIds.framework = framework;
        HmiLanguage.set(framework);
    }

    protected Screen createScreen(int id, int terminal) {
        int which = indexOf(TerminalModeIds.SCREENS, id);
        if (which >= 0) {
            try {
                return settingsScreen(which, terminal);
            } catch (Throwable t) {
                log("screen " + id + " failed: " + t);
                return this.createDefaultScreen(id, terminal);
            }
        }
        Screen screen = super.createScreen(id, terminal);
        if (id == TerminalModeIds.SCREEN_NO_DEVICE) {
            try {
                addEntryRow(screen);
            } catch (Throwable t) {
                log("no-device row failed, screen left stock: " + t);
            }
        }
        return screen;
    }

    public IPartialPopupController getPartialPopup(int id, int terminal) {
        int note = indexOf(TerminalModeIds.POPUPS_NOTE, id);
        if (note >= 0) {
            try {
                return (IPartialPopupController)notePopup(id, TerminalModeIds.TEXTS_NOTE[note], terminal);
            } catch (Throwable t) {
                log("note popup failed: " + t);
                return null;
            }
        }
        return super.getPartialPopup(id, terminal);
    }

    /* As the stock stub of 3200007, plus ours with the same priority, slot and type. */
    public IPartialPopupController[] getPartialPopupStubs(int terminal) {
        IPartialPopupController[] stock = super.getPartialPopupStubs(terminal);
        try {
            int length = stock == null ? 0 : stock.length;
            int ours = TerminalModeIds.POPUPS_NOTE.length;
            IPartialPopupController[] all = new IPartialPopupController[length + ours];
            if (length > 0) {
                System.arraycopy(stock, 0, all, 0, length);
            }
            for (int i = 0; i < ours; i++) {
                all[length + i] = new PartialPopupStub(TerminalModeIds.POPUPS_NOTE[i], -1, -1, 250, 0, 12, true, 7,
                    terminal, 1, null, true, true);
            }
            return all;
        } catch (Throwable t) {
            log("note popup stubs failed: " + t);
            return stock;
        }
    }

    public String getText(int id) {
        String ours = TerminalModeIds.text(id);
        return ours != null ? ours : super.getText(id);
    }

    public String getAltText(int id) {
        String ours = TerminalModeIds.text(id);
        return ours != null ? ours : super.getAltText(id);
    }

    public String getText(int id, int terminal) {
        String ours = TerminalModeIds.text(id);
        return ours != null ? ours : super.getText(id, terminal);
    }

    private void addEntryRow(Screen screen) {
        MenuItemController stock = findItemWithEvent((AbstractWidget)screen, TerminalModeIds.EVENT_TO_CONNECTION_MANAGER);
        if (stock == null) {
            throw new IllegalStateException("row with event " + TerminalModeIds.EVENT_TO_CONNECTION_MANAGER + " not found");
        }
        MenuController menu = (MenuController)stock.getParent();
        MenuItemController row = actionRow(TerminalModeIds.EVENTS_OPEN[Settings.SCREEN_MAIN], TerminalModeIds.TEXT_ENTRY_ROW,
            TerminalModeIds.INTERNAL_ENTRY_ROW);
        menu.addWithPosition(row, menu.getChildren().indexOf(stock) + 1);
        log("row added to the no-device screen");
    }

    private static MenuItemController findItemWithEvent(AbstractWidget widget, int event) {
        if (widget instanceof MenuItemController && widget.getEvent() == event
                && widget.getParent() instanceof MenuController) {
            return (MenuItemController)widget;
        }
        List children = widget.getChildren();
        if (children == null) {
            return null;
        }
        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            if (child instanceof AbstractWidget) {
                MenuItemController found = findItemWithEvent((AbstractWidget)child, event);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /* As the stock "Switch to connection manager" row. */
    private static MenuItemController actionRow(int event, int label, int internalId) {
        MenuItemController row = new MenuItemController();
        row.setRenderer(new CompositeRendererHigh(row));
        row.setEvent(event);
        row.setLabelId(label);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(7);
        row.setInternalID(internalId);
        row.setType(2);   /* TYPE_ACTION */
        return row;
    }

    /* As the stock "Bluetooth audio player" checkbox of Bluetooth settings. */
    private static MenuItemController checkboxRow(int model, int label, int internalId) {
        MenuItemController row = new MenuItemController();
        CompositeRendererHigh renderer = new CompositeRendererHigh(row);
        row.setRenderer(renderer);
        renderer.setKzbIDs(new int[] {25});
        row.setModelID(model);
        row.setLabelId(label);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(7);
        row.setInternalID(internalId);
        row.setType(8);   /* TYPE_CHECKBOX */
        return row;
    }

    /* As the stock submenu rows of Connection manager's options (arrow bitmap 39); opens Settings.SCREEN_*. */
    private static MenuItemController submenuRow(int screen) {
        MenuItemController row = new MenuItemController();
        row.setRenderer(new CompositeRendererHigh(row));
        row.setBitmaps(new int[] {39, 39, 39, 39});
        row.setEvent(TerminalModeIds.EVENTS_OPEN[screen]);
        row.setLabelId(TerminalModeIds.TEXT_SCREENS[screen]);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(7);
        row.setInternalID(TerminalModeIds.INTERNAL_SUBMENUS[screen]);
        row.setType(4);   /* TYPE_SUBMENU */
        return row;
    }

    /* As the stock model-bound action rows of Connection manager's options (model, no event). */
    private static MenuItemController restartRow() {
        MenuItemController row = new MenuItemController();
        row.setRenderer(new CompositeRendererHigh(row));
        row.setModelID(TerminalModeIds.MODEL_RESTART);
        row.setLabelId(TerminalModeIds.TEXT_RESTART);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(7);
        row.setInternalID(TerminalModeIds.INTERNAL_RESTART);
        row.setType(2);   /* TYPE_ACTION */
        return row;
    }

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /* One of Settings.SCREEN_*: its checkboxes, and on the main screen the submenus and "Restart CarPlay". */
    private Screen settingsScreen(int which, int terminal) {
        List rows = new ArrayList();
        for (int i = 0; i < Settings.ALL.length; i++) {
            if (Settings.ALL[i].screen != which || !Settings.ALL[i].shown()) {
                continue;
            }
            MenuItemController row = checkboxRow(TerminalModeIds.FIRST_MODEL + i, TerminalModeIds.FIRST_TEXT_SETTING + i,
                TerminalModeIds.FIRST_INTERNAL_SETTING + i);
            if (Settings.ALL[i].hintKey() != null) {
                /* stock sets only disabled-row infolines; the enabled one is the same widget API */
                row.setInfolineTextIdEnabled(TerminalModeIds.FIRST_TEXT_HINT + i);
            }
            rows.add(row);
        }
        if (which == Settings.SCREEN_MAIN) {
            if (new File(Settings.RGD_JAR).exists()) {
                rows.add(submenuRow(Settings.SCREEN_ROUTE_GUIDANCE));
            }
            if (FlagStore.isPresent(TerminalModeIds.COCKPIT_MAP_FLAG)) {
                rows.add(submenuRow(Settings.SCREEN_COCKPIT_MAP));
            }
            rows.add(restartRow());
        }
        return menuScreen(TerminalModeIds.SCREENS[which], TerminalModeIds.TEXT_SCREENS[which], terminal, rows);
    }

    /* Our rows; each row bound to one of our models is that model's view (the status bar is model 138). */
    private Screen menuScreen(int id, int titleText, int terminal, List rows) {
        MenuController menu = settingsMenu(terminal);
        List bound = new ArrayList();
        for (int i = 0; i < rows.size(); i++) {
            MenuItemController row = (MenuItemController)rows.get(i);
            menu.add(row);
            if (row.getModelID() >= TerminalModeIds.FIRST_MODEL && row.getModelID() <= TerminalModeIds.MODEL_RESTART) {
                bound.add(row);
            }
        }
        int[] viewIds = new int[bound.size() + 1];
        HMIView[][] views = new HMIView[bound.size() + 1][];
        for (int i = 0; i < bound.size(); i++) {
            MenuItemController row = (MenuItemController)bound.get(i);
            viewIds[i + 1] = row.getModelID();
            views[i + 1] = new HMIView[] {row};
        }
        ContainerController content = container(100, 0.5f, 0.25f);
        content.add(menu);

        StatusBarStubController statusBar = new StatusBarStubController();
        statusBar.setModelID(138);
        statusBar.setBounds(0, 0, 100, 100);

        ScreenWidgetEVO screen = new ScreenWidgetEVO(id);
        screen.setScreenFactory((AbstractScreenFactory)this);
        ScreenRendererHigh renderer = new ScreenRendererHigh((AbstractScreenWidget)screen);
        screen.setRenderer((ScreenWidgetRenderer)renderer);
        renderer.setFonts(fonts(0, terminal));
        screen.setColorIndices(new int[] {1, 2, 4, 0});
        screen.setColorPalettes(PALETTES);
        screen.setSmallStageType(1);
        screen.add(content, 1);
        screen.add(titleBar(titleText, terminal), 2);
        screen.add(statusBar);
        viewIds[0] = 138;
        views[0] = new HMIView[] {statusBar};
        screen.setViews(viewIds, views);
        return screen;
    }

    /* The no-device screen's menu frame, laid out as a settings list. */
    private MenuController settingsMenu(int terminal) {
        GlassplateController glassplate = new GlassplateController();
        GlassplateRendererHigh glassplateRenderer = new GlassplateRendererHigh(glassplate);
        glassplate.setRenderer((IRenderer)glassplateRenderer);
        glassplateRenderer.setKzbIDs(new int[] {25});
        glassplate.setBounds(0, 0, 100, 100);
        glassplate.setSdsNumbersGapWidth(4);
        glassplate.setSdsNumbersWidth(64);

        ScrollbarController scrollbar = new ScrollbarController();
        ScrollbarRendererKanziHigh scrollbarRenderer = new ScrollbarRendererKanziHigh(scrollbar);
        scrollbar.setRenderer((ScrollbarRenderer)scrollbarRenderer);
        scrollbarRenderer.setKzbIDs(new int[] {25});
        scrollbar.setBounds(933, 8, 5, 308);

        FocusCursorController cursor = new FocusCursorController();
        FocusCursorRendererHigh cursorRenderer = new FocusCursorRendererHigh((CursorController)cursor);
        cursor.setRenderer((CursorRenderer)cursorRenderer);
        cursorRenderer.setKzbIDs(new int[] {25, 25});
        cursor.setBounds(0, 0, 100, 100);
        cursor.setColorIndices(new int[] {1, 2, 4, 0});

        MenuController menu = new MenuController();
        MenuRendererHigh menuRenderer = new MenuRendererHigh(menu);
        menu.setRenderer((CompositeRenderer)menuRenderer);
        menu.createInfolineWidgets((IInfoLineRenderer)new InfoLineRendererHigh());
        menuRenderer.setKzbIDs(new int[] {25});
        menu.getInfoline().getInfolineRenderer().setFonts((Object[])fonts(1, terminal));
        menu.setDefaultDisabledInfolineTextId(TEXT_MENU_DISABLED);
        menu.getInfolineTimer().setIdleTime(800);
        menu.getInfoline().setColorIndices(new int[] {2, 0, 4, 3});
        menu.getLayout().setBackgroundInsetsVert(7);
        menu.getLayout().setContentLeftOffset(12);
        menu.getLayout().setContentRightOffset(25);
        menu.getLayout().setContentRightOffsetSmallStage(25);
        menu.getLayout().setItemsGap(9);
        menu.getLayout().setOptionsIconAdditionalRightInsets(26);
        menu.setBounds(59, 77, 946, 324);
        menu.setColorIndices(new int[] {1, 0, 4, 0, 2, 3});
        menu.add(glassplate, 4);
        menu.add(scrollbar, 3);
        menu.add(cursor, 1);
        return menu;
    }

    private ContainerController titleBar(int titleText, int terminal) {
        IconController icon = new IconController();
        IconRendererHigh iconRenderer = new IconRendererHigh(icon);
        icon.setRenderer((IconRenderer)iconRenderer);
        icon.setBitmaps(new int[] {127});
        icon.setBounds(0, 0, 100, 100);

        LabelController title = new LabelController();
        LabelRendererHigh titleRenderer = new LabelRendererHigh(title);
        title.setRenderer((LabelRenderer)titleRenderer);
        titleRenderer.setFonts(fonts(2, terminal));
        title.setTextIds(new int[] {titleText});
        title.setBounds(0, 0, 0, 30);
        title.setColorIndices(new int[] {1, 2, 4, 0});

        LabelController breadcrumb = new LabelController();
        LabelRendererHigh breadcrumbRenderer = new LabelRendererHigh(breadcrumb);
        breadcrumb.setRenderer((LabelRenderer)breadcrumbRenderer);
        breadcrumb.setTextIds(new int[] {TEXT_BREADCRUMB});
        breadcrumb.setBounds(0, 0, 0, 30);
        breadcrumb.setCoordinateSets(new int[] {1, 0, 0, 0, 30, 0, 0, 0, 30});

        TitleBarWidget bar = new TitleBarWidget();
        TitleBarRendererHigh barRenderer = new TitleBarRendererHigh(bar);
        bar.setRenderer((CompositeRenderer)barRenderer);
        bar.setBitmaps(new int[] {58, 58});
        bar.getTitleLayout().setGapInsideBreadcrumbs(15);
        bar.getTitleLayout().setGapInsideTitles(15);
        bar.setBounds(187, 28, 650, 20);
        bar.setColorIndices(new int[] {1, 8, 4, 0});
        bar.addLeftIcon((AbstractWidgetController)icon);
        bar.addTitle((AbstractWidgetController)title);
        bar.addBreadcrumb((AbstractWidgetController)breadcrumb);

        ContainerController container = container(1024, 0.0f, 0.0f);
        container.add(bar);
        return container;
    }

    /* As the stock "Bluetooth activated" partial popup (3200007): a text note that
     * hides after 3 s, with the stock layout, fonts and key consumption. */
    private Screen notePopup(int id, int textId, int terminal) {
        PartialPopupGlassplateController glassplate = new PartialPopupGlassplateController();
        PartialPopupGlassplateRendererHigh glassplateRenderer = new PartialPopupGlassplateRendererHigh(glassplate);
        glassplate.setRenderer((IRenderer)glassplateRenderer);
        glassplateRenderer.setKzbIDs(new int[] {25});
        glassplate.setBounds(0, 0, 100, 100);
        glassplate.setSeparatorVisible(true);
        glassplate.setSeparatorYPosition(143);

        LabelController text = new LabelController();
        MultiLineLabelRendererHigh textRenderer = new MultiLineLabelRendererHigh(text);
        text.setRenderer((LabelRenderer)textRenderer);
        text.setTextIds(new int[] {textId});
        text.setBounds(0, 0, 0, 151);
        textRenderer.setAlignment(2, 4);

        FocusCursorController cursor = new FocusCursorController();
        FocusCursorRendererHigh cursorRenderer = new FocusCursorRendererHigh((CursorController)cursor);
        cursor.setRenderer((CursorRenderer)cursorRenderer);
        cursorRenderer.setKzbIDs(new int[] {25, 25});
        cursor.setBounds(0, 0, 100, 100);
        cursor.setColorIndices(new int[] {1, 2, 4, 0});
        cursor.setOptionsIconVisible(false);

        MenuController options = new MenuController();
        MenuRendererHigh optionsRenderer = new MenuRendererHigh(options);
        options.setRenderer((CompositeRenderer)optionsRenderer);
        options.createInfolineWidgets((IInfoLineRenderer)new InfoLineRendererHigh());
        optionsRenderer.setKzbIDs(new int[] {25});
        options.getInfoline().getInfolineRenderer().setFonts((Object[])fonts(1, terminal));
        options.setDefaultDisabledInfolineTextId(3200074);
        options.getInfolineTimer().setIdleTime(800);
        options.getInfoline().setColorIndices(new int[] {2, 0, 4, 3});
        options.getLayout().setBackgroundInsetsVert(7);
        options.getLayout().setContentLeftOffset(12);
        options.getLayout().setContentRightOffset(25);
        options.getLayout().setContentRightOffsetSmallStage(25);
        options.getLayout().setItemsGap(9);
        options.getLayout().setOptionsIconAdditionalRightInsets(26);
        options.setBounds(0, 148, 805, 101);
        options.setCalculatePreferredSize(true);
        options.setColorIndices(new int[] {1, 0, 4, 0, 2, 3});
        options.setCoordinateSets(new int[] {3, 0, 148, 805, 101, 551, 423, 337, 214});
        options.add(cursor, 1);

        InstructionTextContoller content = new InstructionTextContoller();
        CompositeRendererHigh contentRenderer = new CompositeRendererHigh(content);
        content.setRenderer((CompositeRenderer)contentRenderer);
        content.setBounds(0, 0, 100, 100);
        content.setColorIndices(new int[] {1, 0, 4, 0});
        content.addHintText(text);
        content.setOptions(options);

        PartialPopupController popup = new PartialPopupController(id);
        popup.setScreenFactory((AbstractScreenFactory)this);
        PartialPopupRendererHigh popupRenderer = new PartialPopupRendererHigh(popup);
        popup.setRenderer((PartialPopupRenderer)popupRenderer);
        popupRenderer.setFonts(fonts(0, terminal));
        popup.setAutoHideTime(3000);
        popup.setBounds(512, 400, 805, 0);
        popup.setColorIndices(new int[] {1, 2, 4, 0});
        popup.setColorPalettes(PALETTES);
        popup.setConsumeDDSPress(1);
        popup.setConsumeHKReturn(1);
        popup.setConsumeJoystickEast(3);
        popup.setConsumeJoystickNorth(3);
        popup.setConsumeJoystickSouth(3);
        popup.setConsumeJoystickWest(3);
        popup.setConsumeKeyTurned(2);
        popup.setConsumeSKPress(3);
        popup.setConsumeTouchPad(4);
        popup.setContentInset(10);
        popup.setDynamicSize(true);
        popup.setGreyOutBackground(true);
        popup.setID(id);
        popup.setSmallStageType(1);
        popup.setStyle(1);
        popup.setSurviveScreenChange(true);
        popup.setType(7);
        popup.setZpmPriority(250);
        popup.setZpmSlot(12);
        popup.setBackgroundController(glassplate);
        popup.add(content);
        return popup;
    }

    /* The stock content/title containers, which differ in width and pivots only. */
    private static ContainerController container(int width, float pivot, float selectionPivot) {
        ContainerController container = new ContainerController();
        ContainerRendererHigh renderer = new ContainerRendererHigh(container);
        container.setRenderer((CompositeRenderer)renderer);
        container.setBounds(0, 0, width, width == 1024 ? 0 : 100);
        container.setEntertainmentMenuTransformation(12.0f, 12.0f, 0.96f, 0.96f, pivot);
        container.setOpacitySet(1.0f, 0.4f);
        container.setOptionMenuTransformation(-4.0f, 7.0f, 0.6f, 0.6f, pivot);
        container.setSelectionMenuTransformation(391.0f, 7.0f, 0.6f, 0.6f, selectionPivot);
        return container;
    }

    private IWrappedFont[] fonts(int set, int terminal) {
        return FontFactory.getFonts(set, terminal, this.getFramework());
    }

    private static void log(String message) {
        Log.log("factory: " + message);
    }
}
