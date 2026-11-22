package com.coreyd97.burpcustomizer;

import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads the built extension jar the way Burp does: through a classloader that only exposes the JDK, the Montoya
 * API and com.formdev.flatlaf.ui from the host. Running the extension on the shared test classpath would hide
 * classloading problems, so this test goes through the real jar end to end.
 * Needs a Burp jar on the test classpath: ./gradlew test -PburpJar=/path/to/burpsuite.jar
 */
class ExtensionIsolationTest {

    private static final String ENTRY_POINT = "com.coreyd97.burpcustomizer.bootstrap.CustomizerBootstrap";

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

    private LookAndFeel burpTheme;
    private FakeMontoya montoya;
    private URLClassLoader extensionLoader;

    @BeforeAll
    static void requireBurp() {
        assumeTrue(isOnClasspath("burp.theme.BurpLaf"), "Burp jar not on classpath, pass -PburpJar=/path/to/burpsuite.jar");
    }

    @BeforeEach
    void startBurp() throws Exception {
        burpTheme = burpDarkTheme();
        onEdt(() -> UIManager.setLookAndFeel(burpTheme));
        Logger.getLogger(FlatLaf.class.getName()).addHandler(errorCollector);
        montoya = new FakeMontoya();
        extensionLoader = new BurpExtensionClassLoader(new File(System.getProperty("extensionJar")).toURI().toURL(),
                ExtensionIsolationTest.class.getClassLoader());
    }

    @AfterEach
    void stopBurp() throws Exception {
        Logger.getLogger(FlatLaf.class.getName()).removeHandler(errorCollector);
        extensionLoader.close();
        onEdt(() -> UIManager.setLookAndFeel(burpTheme));
    }

    @ParameterizedTest
    @CsvSource({
            "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme, Dracula, true",
            "com.coreyd97.burpcustomizer.CarbonfoxTheme, Carbonfox, true",
            "com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme, GitHub, false",
    })
    void appliesSavedThemeOnBurpsOwnFlatLaf(String themeClass, String themeName, boolean dark) throws Exception {
        montoya.preferences.put("theme", themeClass);

        loadExtension();

        assertTrue(montoya.errors.isEmpty(), () -> "Extension logged errors: " + montoya.errors);
        assertNotNull(montoya.suiteTab, "Customizer tab was not registered");
        LookAndFeel applied = UIManager.getLookAndFeel();
        assertEquals(themeName, applied.getName());
        //Must be Burp's FlatLaf, not a bundled copy, or Burp's components cannot tell the theme apart.
        assertInstanceOf(FlatLaf.class, applied);
        assertEquals(dark, FlatLaf.isLafDark(), "Burp's FlatLaf disagrees about dark mode");
        //Burp's own UI classes are referenced by name from BurpLaf.properties and must resolve.
        assertEquals("burp.theme.BurpFlatScrollPaneBorder", UIManager.getBorder("ScrollPane.border").getClass().getName());
        assertTrue(flatLafErrors.isEmpty(), () -> "FlatLaf reported errors: " + flatLafErrors.stream()
                .map(r -> r.getMessage() + (r.getThrown() != null ? " " + r.getThrown() : "")).toList());
        assertEquals(themeClass, montoya.preferences.get("theme"));
    }

    @Test
    void restoresBurpThemeWhenUnloaded() throws Exception {
        montoya.preferences.put("theme", "com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme");
        loadExtension();
        assertEquals("Dracula", UIManager.getLookAndFeel().getName());

        for (Runnable handler : montoya.unloadingHandlers) handler.run();
        flushEdt();

        assertSame(burpTheme, UIManager.getLookAndFeel());
        assertTrue(montoya.errors.isEmpty(), () -> "Extension logged errors: " + montoya.errors);
    }

    private void loadExtension() throws Exception {
        Class<?> entryPoint = extensionLoader.loadClass(ENTRY_POINT);
        Object extension = entryPoint.getDeclaredConstructor().newInstance();
        Class<?> api = Class.forName("burp.api.montoya.MontoyaApi");
        entryPoint.getMethod("initialize", api).invoke(extension, montoya.proxy(api));
        flushEdt();
    }

    private static LookAndFeel burpDarkTheme() throws Exception {
        Class<?> factory = Class.forName("burp.theme.ThemeFactory");
        return (LookAndFeel) factory.getMethod("loadBuiltIn", String.class).invoke(null, "burp/theme/DarkTheme.json");
    }

    private static boolean isOnClasspath(String className) {
        try {
            Class.forName(className, false, ExtensionIsolationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private interface EdtAction {
        void run() throws Exception;
    }

    private static void onEdt(EdtAction action) throws Exception {
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.run();
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) throw failure[0];
    }

    private static void flushEdt() throws Exception {
        //Twice: work queued on the EDT may queue more work.
        SwingUtilities.invokeAndWait(() -> {
        });
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    /**
     * Mirrors Burp's extension classloader policy: the JDK comes from the platform loader, only the Montoya API
     * and FlatLaf's UI delegates are shared from the host, everything else must come from the extension jar.
     */
    static final class BurpExtensionClassLoader extends URLClassLoader {
        private static final List<String> SHARED_HOST_PACKAGES = List.of("burp.api.montoya.", "com.formdev.flatlaf.ui.");
        private final ClassLoader host;

        BurpExtensionClassLoader(URL extensionJar, ClassLoader host) {
            super(new URL[]{extensionJar}, ClassLoader.getPlatformClassLoader());
            this.host = host;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String shared : SHARED_HOST_PACKAGES) {
                if (name.startsWith(shared)) return host.loadClass(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    /** Just enough of the Montoya API for the extension: preferences, logging, a suite tab and unload handlers. */
    static final class FakeMontoya {
        final Map<String, String> preferences = new HashMap<>();
        final List<String> errors = new ArrayList<>();
        final List<Runnable> unloadingHandlers = new ArrayList<>();
        Object suiteTab;

        Object proxy(Class<?> type) {
            InvocationHandler handler = (proxy, method, args) -> invoke(type, method, args);
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        private Object invoke(Class<?> type, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "getString":
                    return preferences.get((String) args[0]);
                case "setString":
                    preferences.put((String) args[0], (String) args[1]);
                    return null;
                case "logToError":
                    errors.add(String.valueOf(args[0]) + (args.length > 1 ? " " + args[1] : ""));
                    return null;
                case "registerSuiteTab":
                    suiteTab = args[1];
                    return null;
                case "registerUnloadingHandler":
                    Object unloadingHandler = args[0];
                    Method extensionUnloaded = method.getParameterTypes()[0].getMethod("extensionUnloaded");
                    unloadingHandlers.add(() -> {
                        try {
                            extensionUnloaded.invoke(unloadingHandler);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    return null;
                case "toString":
                    return "FakeMontoya " + type.getSimpleName();
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return false;
                default:
                    Class<?> returnType = method.getReturnType();
                    if (returnType.isInterface()) return proxy(returnType);
                    if (returnType == boolean.class) return false;
                    if (returnType.isPrimitive() && returnType != void.class) return 0;
                    return null;
            }
        }
    }
}
