package com.coreyd97.burpcustomizer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Burp's extension classloader only exposes a few host packages (e.g. com.formdev.flatlaf.ui) to extensions.
 * Everything else the extension uses must be bundled in its own jar, or it fails with ClassNotFoundException.
 */
class ExtensionJarTest {

    private static final List<String> REQUIRED_ENTRIES = List.of(
            "burp/BurpExtender.class",
            "com/formdev/flatlaf/FlatLaf.class",
            "com/formdev/flatlaf/IntelliJTheme.class",
            "com/formdev/flatlaf/util/ColorFunctions.class",
            "com/formdev/flatlaf/extras/FlatInspector.class",
            "com/formdev/flatlaf/extras/FlatUIDefaultsInspector.class",
            "com/formdev/flatlaf/intellijthemes/FlatAllIJThemes.class",
            "com/coreyd97/burpcustomizer/themes/Carbonfox.theme.json"
    );

    @Test
    void bundlesEverythingBurpDoesNotExposeToExtensions() throws IOException {
        try (JarFile jar = openExtensionJar()) {
            for (String entry : REQUIRED_ENTRIES) {
                assertNotNull(jar.getEntry(entry), "Extension jar is missing " + entry);
            }
        }
    }

    @Test
    void doesNotBundleTheMontoyaApi() throws IOException {
        //Burp always provides its own copy, a bundled one is dead weight.
        try (JarFile jar = openExtensionJar()) {
            assertNull(jar.getEntry("burp/api/montoya/MontoyaApi.class"), "Montoya API must not be bundled");
        }
    }

    private static JarFile openExtensionJar() throws IOException {
        String jarPath = System.getProperty("extensionJar");
        assertNotNull(jarPath, "extensionJar system property must point to the built extension jar");
        return new JarFile(jarPath);
    }
}
