// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount;

import de.audi.atip.base.IFrameworkAccess;
import de.audi.atip.i18n.Language;

/* The HMI's current language as an ISO 639-1 code, asked as stock widgets ask; null if unknown (English then). */
public final class HmiLanguage {
    private static IFrameworkAccess framework;

    private HmiLanguage() {
    }

    public static void set(IFrameworkAccess access) {
        if (framework == null) {
            framework = access;
        }
    }

    public static String iso() {
        try {
            Language language = framework.getLanguageMgr().getCurrentLanguage("LANG_COMPONENT_HMI");
            return language.getLocale().getLanguage();
        } catch (Throwable t) {
            return null;
        }
    }
}
