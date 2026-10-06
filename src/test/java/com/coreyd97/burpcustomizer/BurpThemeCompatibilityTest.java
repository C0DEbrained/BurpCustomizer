package com.coreyd97.burpcustomizer;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Applies every bundled theme on top of Burp's own LaF classes.
 * Needs a Burp jar on the test classpath: ./gradlew test -PburpJar=/path/to/burpsuite.jar
 * Without it these tests are skipped.
 */
class BurpThemeCompatibilityTest {

    //Colours Burp's own components read; a theme that leaves them unset renders Burp unusable.
    private static final String[] REQUIRED_COLOURS = {
            "Panel.background",
            "Label.foreground",
            "Colors.ui.background.1",
            "Colors.ui.text.body",
            "Colors.palette.mono.3",
            "Colors.palette.primary.5",
            "Colors.ui.editor.message.text",
            "Button.selectedBackground",
    };

    private final List<LogRecord> flatLafErrors = new ArrayList<>();
    private final Handler errorCollector = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) flatLafErrors.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeAll
    static void requireBurp() {
        System.setProperty("java.awt.headless", "true");
        assumeTrue(isBurpOnClasspath(), "Burp jar not on classpath, pass -PburpJar=/path/to/burpsuite.jar");
    }

    @BeforeEach
    void collectFlatLafErrors() {
        Logger.getLogger(FlatLaf.class.getName()).addHandler(errorCollector);
    }

    @AfterEach
    void stopCollecting() {
        Logger.getLogger(FlatLaf.class.getName()).removeHandler(errorCollector);
    }

    static List<String> bundledThemes() {
        return new BurpCustomizer().getThemes().stream().map(UIManager.LookAndFeelInfo::getClassName).toList();
    }

    @ParameterizedTest
    @MethodSource("bundledThemes")
    void appliesCleanlyOnBurp(String themeClass) throws Exception {
        IntelliJTheme.ThemeLaf base = (IntelliJTheme.ThemeLaf) Class.forName(themeClass).getDeclaredConstructor().newInstance();
        UIManager.setLookAndFeel(new CustomTheme(base, false));

        assertTrue(flatLafErrors.isEmpty(), () -> "FlatLaf reported errors: " + flatLafErrors.stream()
                .map(LogRecord::getMessage).toList());
        for (String key : REQUIRED_COLOURS) {
            assertNotNull(UIManager.getColor(key), key + " is not set");
        }
    }

    @Test
    void carbonfoxOverridesBurpColours() throws Exception {
        UIManager.setLookAndFeel(new CustomTheme(new CarbonfoxTheme(), false));

        assertEquals(new Color(0x161616), UIManager.getColor("Colors.ui.editor.message.background"));
        assertEquals(new Color(0xee5396), UIManager.getColor("Button.selectedBackground"));
        assertEquals(new Color(0xee5396), UIManager.getColor("Colors.ui.highlight.0.background"));
        assertEquals(new Color(0x78a9ff), UIManager.getColor("Colors.palette.primary.core"));
    }

    private static boolean isBurpOnClasspath() {
        try {
            Class.forName("burp.theme.BurpLaf");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
