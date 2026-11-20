// SPDX-License-Identifier: GPL-3.0-or-later
// Host-test stand-in for the stock widget (after luka-dev's tests/stubs/pdc): only the
// physical HMI widget boundary is replaced; nothing here ships.
package de.esolutions.hmi.widgets.audi.base;

import de.audi.atip.hmi.view.Screen;

public class AbstractScreenWidget implements Screen {
    private final int id;
    private int smallStage = 1;

    public AbstractScreenWidget(int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public boolean isPartialPopupBlocked(de.audi.atip.hmi.view.IPartialPopupController popup) {
        return false;
    }

    public int getSmallStageType() {
        return smallStage;
    }

    public void setSmallStageType(int value) {
        smallStage = value;
    }
}
