package com.coreyd97.burpcustomizer;

import java.util.Properties;

/**
 * Implemented by bundled themes that want to set Burp specific colour variables
 * (e.g. Colors.ui.editor.message.*) on top of the palette derived by {@link CustomTheme}.
 */
public interface BurpThemeOverrides {
    Properties getBurpOverrides();
}
