package com.coreyd97.burpcustomizer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout of the extension jar. Burp only sees the bootstrap; the implementation and its private libraries
 * live in a nested jar, and everything Burp already provides (FlatLaf, Montoya) must not be bundled.
 */
class ExtensionJarTest {

    private static final String BOOTSTRAP = "com/coreyd97/burpcustomizer/bootstrap/CustomizerBootstrap.class";
    private static final String IMPLEMENTATION_JAR = "burpcustomizer-impl.jar";

    private static final List<String> REQUIRED_IMPLEMENTATION_ENTRIES = List.of(
            "com/coreyd97/burpcustomizer/BurpCustomizer.class",
            "com/coreyd97/burpcustomizer/themes/Carbonfox.theme.json",
            "com/formdev/flatlaf/intellijthemes/FlatAllIJThemes.class",
            "com/coreyd97/BurpExtenderUtilities/PanelBuilder.class",
            "say/swing/JFontChooser.class"
    );

    //Provided by Burp. A bundled copy would be a second, incompatible FlatLaf or a redundant Montoya API.
    private static final List<String> HOST_PACKAGES = List.of(
            "com/formdev/flatlaf/FlatLaf.class",
            "com/formdev/flatlaf/IntelliJTheme.class",
            "com/formdev/flatlaf/ui/",
            "com/formdev/flatlaf/util/",
            "com/formdev/flatlaf/extras/",
            "burp/api/montoya/"
    );

    @Test
    void outerJarOnlyContainsTheBootstrap() throws IOException {
        try (JarFile jar = openExtensionJar()) {
            List<String> classes = jar.stream().map(JarEntry::getName).filter(n -> n.endsWith(".class")).toList();
            assertEquals(List.of(BOOTSTRAP), classes, "Burp must only see the bootstrap class");
            assertNotNull(jar.getEntry(IMPLEMENTATION_JAR), "Extension jar is missing " + IMPLEMENTATION_JAR);
        }
    }

    @Test
    void implementationJarBundlesWhatBurpDoesNotProvide() throws IOException {
        List<String> entries = implementationEntries();
        for (String required : REQUIRED_IMPLEMENTATION_ENTRIES) {
            assertTrue(entries.contains(required), "Implementation jar is missing " + required);
        }
    }

    @Test
    void doesNotBundleHostClasses() throws IOException {
        List<String> entries = implementationEntries();
        for (String host : HOST_PACKAGES) {
            assertFalse(entries.stream().anyMatch(e -> e.startsWith(host)), host + " must come from Burp, not be bundled");
        }
    }

    private static List<String> implementationEntries() throws IOException {
        try (JarFile jar = openExtensionJar();
             InputStream nested = jar.getInputStream(jar.getEntry(IMPLEMENTATION_JAR));
             JarInputStream in = new JarInputStream(nested)) {
            List<String> names = new ArrayList<>();
            for (JarEntry e = in.getNextJarEntry(); e != null; e = in.getNextJarEntry()) names.add(e.getName());
            return names;
        }
    }

    private static JarFile openExtensionJar() throws IOException {
        String jarPath = System.getProperty("extensionJar");
        assertNotNull(jarPath, "extensionJar system property must point to the built extension jar");
        return new JarFile(jarPath);
    }
}
