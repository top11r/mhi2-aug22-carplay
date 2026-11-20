// SPDX-License-Identifier: GPL-3.0-or-later
// Host-test stand-in for the stock interface (after luka-dev's tests/stubs/pdc): only the
// physical HMI widget boundary is replaced; nothing here ships.
package de.audi.atip.hmi.view;

public interface Screen {
    int getID();

    boolean isPartialPopupBlocked(IPartialPopupController popup);
}
