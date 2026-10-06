package com.coreyd97.burpcustomizer;

import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.util.ColorFunctions;

import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Properties;

public class CustomTheme extends IntelliJTheme.ThemeLaf {

    Class burpLaf, burpDark, burpLight;
    private final boolean isPreview;

    public CustomTheme(IntelliJTheme.ThemeLaf base, boolean isPreview) {
        super(base.getTheme());
        this.isPreview = isPreview;
        try {
            this.burpLaf = ClassLoader.getSystemClassLoader().loadClass("burp.theme.BurpLaf");
        }catch (Exception e){
            throw new RuntimeException("Cannot find required Burp themes. " +
                    "This shouldn't happen as we shouldn't try to switch the theme if it's not supported.");
        }
        //Burp versions before 2025 shipped separate dark/light LaF classes.
        //Newer versions build them from JSON swatches instead, see getBurpThemeProperties().
        this.burpDark = loadOptionalClass("burp.theme.BurpDarkLaf");
        this.burpLight = loadOptionalClass("burp.theme.BurpLightLaf");
    }

    private static Class loadOptionalClass(String name) {
        try {
            return ClassLoader.getSystemClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    protected ArrayList<Class<?>> getLafClassesForDefaultsLoading() {
        ArrayList<Class<?>> lafClasses = super.getLafClassesForDefaultsLoading();
        lafClasses.remove(this.getTheme().getClass());
        lafClasses.add(burpLaf);
        Class burpVariant = isDark() ? burpDark : burpLight;
        if (burpVariant != null) lafClasses.add(burpVariant);
        lafClasses.add(this.getTheme().getClass());
        return lafClasses;
    }

    //Newer Burp versions define their colour variables (Colors.*) in burp/theme/{Dark,Light}Theme.json,
    //which BurpLaf.properties references. Without them, most Burp specific components lose their colours.
    private Properties getBurpThemeProperties() {
        try {
            Class themeFactory = ClassLoader.getSystemClassLoader().loadClass("burp.theme.ThemeFactory");
            String resource = isDark() ? "burp/theme/DarkTheme.json" : "burp/theme/LightTheme.json";
            Object burpTheme = themeFactory.getMethod("loadBuiltIn", String.class).invoke(null, resource);
            Method getAdditionalDefaults = burpTheme.getClass().getDeclaredMethod("getAdditionalDefaults");
            getAdditionalDefaults.setAccessible(true);
            return (Properties) getAdditionalDefaults.invoke(burpTheme);
        } catch (Exception e) {
            return new Properties();
        }
    }

    //Map Burp's (2025+) palette onto the selected theme so Burp specific components follow it.
    //Burp uses these variables inside borders and derived colours, so they must be concrete colours, not lazy values.
    private void putBurpPaletteOverrides(Properties defaults) {
        UIDefaults themeDefaults = new IntelliJTheme.ThemeLaf(getTheme()).getDefaults();
        Color background = themeDefaults.getColor("Panel.background");
        Color foreground = themeDefaults.getColor("Label.foreground");
        Color disabledForeground = themeDefaults.getColor("Label.disabledForeground");
        Color accent = themeDefaults.getColor("Component.accentColor");
        if (background == null || foreground == null) return;

        //mono.1 is the base background, mono.8 the strongest foreground.
        for (int i = 1; i <= 5; i++) {
            float amount = (i - 1) * 0.04f;
            Color shade = isDark() ? ColorFunctions.lighten(background, amount) : ColorFunctions.darken(background, amount);
            defaults.put("Colors.palette.mono." + i, toHex(shade));
        }
        defaults.put("Colors.palette.mono.6", toHex(disabledForeground != null ? disabledForeground : foreground));
        defaults.put("Colors.palette.mono.7", toHex(foreground));
        defaults.put("Colors.palette.mono.8", toHex(foreground));
        defaults.put("Colors.palette.mono.core", toHex(background));

        if (accent == null) return;
        defaults.put("Colors.palette.primary.core", toHex(accent));
        for (int i = 1; i <= 8; i++) {
            float amount = Math.abs(4 - i) * 0.08f;
            Color shade = i <= 4 ? ColorFunctions.darken(accent, amount) : ColorFunctions.lighten(accent, amount);
            defaults.put("Colors.palette.primary." + i, toHex(shade));
        }
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public UIDefaults getDefaults() {
        return super.getDefaults();
//        UIDefaults defaults;
//        FlatLaf burpBase;
//        try {
//            if (isDark()) {
//                burpBase = (FlatLaf) burpDark.getConstructor().newInstance();
//            }else{
//                burpBase = (FlatLaf) burpLight.getConstructor().newInstance();
//            }
//            defaults = burpBase.getDefaults();
//
//        }catch (Exception e){
//            defaults = super.getDefaults();
//            BurpCustomizer.montoya.logging().logToError("Could not get Burp base theme! - " + e.getMessage());
//        }
//
//        UIDefaults themeDefaults = super.getDefaults();
//        themeDefaults.entrySet().parallelStream()
//                .filter(e -> e.getKey().toString().matches("\\w+UI$")) //Find UI delegates
//                        .forEach(e -> themeDefaults.remove(e.getKey())); //And remove so we don't overwrite them from burp.
//
//        defaults.putAll(themeDefaults);
//        //For some reason, using lazy loading in getAdditionalDefaults for this property causes issues...
//        defaults.put("TabbedPane.selectedBackground", defaults.get("TabbedPane.background"));
//        return defaults;
    }



    @Override
    protected Properties getAdditionalDefaults() {
        //Add Additional Overrides Here
        //This is actually run BEFORE the theme is loaded, so we need to use lazy loading to pull values from the theme.
        Properties defaults = getBurpThemeProperties();

        if (!defaults.isEmpty()) putBurpPaletteOverrides(defaults);

        //Force the IntellijTheme class into loading the json containing defaults so we can use its values
//        defaults.put("Test", "#00FF00");
        //Color Palettes. 1-8, dark needs lightening, light needs darkening
        defaults.put("@accent", "lazy(Button.focusedBorderColor)");
        defaults.put("ColourPalette.mono0", "lazy(Label.background)");
        defaults.put("[dark]ColourPalette.mono1", "lighten(ColourPalette.mono0,5%,lazy)");
        defaults.put("[dark]ColourPalette.mono2", "lighten(ColourPalette.mono0,10%,lazy)");
        defaults.put("[dark]ColourPalette.mono3", "lighten(ColourPalette.mono0,15%,lazy)");
        defaults.put("[dark]ColourPalette.mono4", "lighten(ColourPalette.mono0,20%,lazy)");
        defaults.put("[dark]ColourPalette.mono5", "lighten(ColourPalette.mono0,25%,lazy)");
        defaults.put("[dark]ColourPalette.mono6", "lighten(ColourPalette.mono0,30%,lazy)");
        defaults.put("[dark]ColourPalette.mono7", "lighten(ColourPalette.mono0,35%,lazy)");
        defaults.put("[dark]ColourPalette.mono8", "lighten(ColourPalette.mono0,40%,lazy)");
        defaults.put("[light]ColourPalette.mono1", "darken(ColourPalette.mono0,5%,lazy)");
        defaults.put("[light]ColourPalette.mono2", "darken(ColourPalette.mono0,10%,lazy)");
        defaults.put("[light]ColourPalette.mono3", "darken(ColourPalette.mono0,15%,lazy)");
        defaults.put("[light]ColourPalette.mono4", "darken(ColourPalette.mono0,20%,lazy)");
        defaults.put("[light]ColourPalette.mono5", "darken(ColourPalette.mono0,25%,lazy)");
        defaults.put("[light]ColourPalette.mono6", "darken(ColourPalette.mono0,30%,lazy)");
        defaults.put("[light]ColourPalette.mono7", "darken(ColourPalette.mono0,35%,lazy)");
        defaults.put("[light]ColourPalette.mono8", "darken(ColourPalette.mono0,40%,lazy)");


        defaults.put("BurpPalette.mono0", "lazy(Label.background)");
        defaults.put("[dark]BurpPalette.mono1", "lighten(BurpPalette.mono0,5%,lazy)");
        defaults.put("[dark]BurpPalette.mono2", "lighten(BurpPalette.mono0,10%,lazy)");
        defaults.put("[dark]BurpPalette.mono3", "lighten(BurpPalette.mono0,15%,lazy)");
        defaults.put("[dark]BurpPalette.mono4", "lighten(BurpPalette.mono0,20%,lazy)");
        defaults.put("[dark]BurpPalette.mono5", "lighten(BurpPalette.mono0,25%,lazy)");
        defaults.put("[dark]BurpPalette.mono6", "lighten(BurpPalette.mono0,30%,lazy)");
        defaults.put("[dark]BurpPalette.mono7", "lighten(BurpPalette.mono0,35%,lazy)");
        defaults.put("[dark]BurpPalette.mono8", "lighten(BurpPalette.mono0,40%,lazy)");
        defaults.put("[dark]BurpPalette.mono9", "lighten(BurpPalette.mono0,45%,lazy)");
        defaults.put("[dark]BurpPalette.mono10", "lighten(BurpPalette.mono0,50%,lazy)");
        defaults.put("[dark]BurpPalette.mono11", "lighten(BurpPalette.mono0,55%,lazy)");
        defaults.put("[light]BurpPalette.mono1", "darken(BurpPalette.mono0,5%,lazy)");
        defaults.put("[light]BurpPalette.mono2", "darken(BurpPalette.mono0,10%,lazy)");
        defaults.put("[light]BurpPalette.mono3", "darken(BurpPalette.mono0,15%,lazy)");
        defaults.put("[light]BurpPalette.mono4", "darken(BurpPalette.mono0,20%,lazy)");
        defaults.put("[light]BurpPalette.mono5", "darken(BurpPalette.mono0,25%,lazy)");
        defaults.put("[light]BurpPalette.mono6", "darken(BurpPalette.mono0,30%,lazy)");
        defaults.put("[light]BurpPalette.mono7", "darken(BurpPalette.mono0,35%,lazy)");
        defaults.put("[light]BurpPalette.mono8", "darken(BurpPalette.mono0,40%,lazy)");
        defaults.put("[light]BurpPalette.mono9", "darken(BurpPalette.mono0,45%,lazy)");
        defaults.put("[light]BurpPalette.mono10", "darken(BurpPalette.mono0,50%,lazy)");
        defaults.put("[light]BurpPalette.mono11", "darken(BurpPalette.mono0,55%,lazy)");
//
        defaults.put("DesignSystemPalette.grey0", "lazy(Label.background)");
        defaults.put("[dark]DesignSystemPalette.grey1", "lighten(DesignSystemPalette.grey0,5%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey2", "lighten(DesignSystemPalette.grey0,10%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey3", "lighten(DesignSystemPalette.grey0,15%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey4", "lighten(DesignSystemPalette.grey0,20%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey5", "lighten(DesignSystemPalette.grey0,25%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey6", "lighten(DesignSystemPalette.grey0,30%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey7", "lighten(DesignSystemPalette.grey0,35%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey8", "lighten(DesignSystemPalette.grey0,40%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey9", "lighten(DesignSystemPalette.grey0,45%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey10", "lighten(DesignSystemPalette.grey0,50%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey11", "lighten(DesignSystemPalette.grey0,55%,lazy)");
        defaults.put("[dark]DesignSystemPalette.grey12", "lighten(DesignSystemPalette.grey0,60%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey1", "darken(DesignSystemPalette.grey0,5%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey2", "darken(DesignSystemPalette.grey0,10%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey3", "darken(DesignSystemPalette.grey0,15%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey4", "darken(DesignSystemPalette.grey0,20%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey5", "darken(DesignSystemPalette.grey0,25%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey6", "darken(DesignSystemPalette.grey0,30%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey7", "darken(DesignSystemPalette.grey0,35%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey8", "darken(DesignSystemPalette.grey0,40%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey9", "darken(DesignSystemPalette.grey0,45%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey10", "darken(DesignSystemPalette.grey0,50%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey11", "darken(DesignSystemPalette.grey0,55%,lazy)");
        defaults.put("[light]DesignSystemPalette.grey12", "darken(DesignSystemPalette.grey0,60%,lazy)");

        defaults.put("ColourPalette.background5", "lazy(BurpPalette.mono0)");
        defaults.put("BurpPalette.blue1", "lazy(BurpPalette.mono2)");
        defaults.put("BurpPalette.blue4", "lazy(BurpPalette.mono4)");
        defaults.put("ColourPalette.blue1", "lazy(BurpPalette.mono2)");
        defaults.put("Burp.dualEmptyPanelLeftBackground", "lazy(BurpPalette.mono2)");
        defaults.put("Burp.collapsibleSidebarSelectedLabelBackground", "@accent");
        defaults.put("Burp.burpOrange", "@accent");
        defaults.put("Burp.primaryButtonBackground", "@accent");
        defaults.put("Burp.tabFlashColour", "@accent");
        defaults.put("Burp.tableFilterBarBorder", "@accent");
        defaults.put("Burp.searchBarBorder", "@accent");

        defaults.put("[dark]Burp.backgrounder", "lighten(Label.background,2%,lazy)");
        defaults.put("[light]Burp.backgrounder", "darken(Label.background,2%,lazy)");
//        defaults.put("DesignSystemPalette.grey2", "$Burp.backgrounder");
        defaults.put("@toolBackground", "$Burp.backgrounder");
        defaults.put("Burp.taskListEntrySelectedHighlight", "lazy(Component.accentColor)");

        defaults.put("Burp.taskListEntry", "lazy(ColourPalette.mono2)");
        defaults.put("Burp.textEditorBackground", "lazy(EditorPane.background)");
        defaults.put("Burp.textEditorCurrentLineBackground", "lazy(EditorPane.background)");
//        defaults.put("Checkbox.icon.focusedSelectedBackground", "@accent");
//        defaults.put("Checkbox.icon.hoverSelectedBackground", "@accent");

        return defaults;
    }
    
}
