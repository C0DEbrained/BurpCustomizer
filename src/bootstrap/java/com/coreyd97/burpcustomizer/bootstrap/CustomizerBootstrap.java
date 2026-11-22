package com.coreyd97.burpcustomizer.bootstrap;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Entry point Burp loads. The extension itself is shipped as a nested jar and loaded here.
 * <p>
 * Burp's extension classloader only exposes the JDK, the Montoya API and {@code com.formdev.flatlaf.ui}
 * to extensions. A theme extension has to run on Burp's own FlatLaf and {@code burp.theme} classes:
 * a second, bundled FlatLaf is not recognised by Burp ({@code FlatLaf.isLafDark()} reports false, Burp's
 * UI delegates and borders fail to load). Loading the implementation with Burp's application classloader
 * as parent makes those host classes visible while keeping the bundled libraries private.
 * <p>
 * Keep this class free of dependencies other than the JDK and the Montoya API.
 */
public class CustomizerBootstrap implements BurpExtension {

    static final String IMPLEMENTATION_JAR = "burpcustomizer-impl.jar";
    static final String IMPLEMENTATION_CLASS = "com.coreyd97.burpcustomizer.BurpCustomizer";

    private URLClassLoader implementationLoader;
    private Path implementationJar;

    @Override
    public void initialize(MontoyaApi montoya) {
        try {
            implementationJar = extractImplementation();
            implementationLoader = new URLClassLoader("BurpCustomizer",
                    new URL[]{implementationJar.toUri().toURL()}, hostClassLoader());

            BurpExtension implementation = (BurpExtension) implementationLoader.loadClass(IMPLEMENTATION_CLASS)
                    .getDeclaredConstructor().newInstance();
            withContextClassLoader(implementationLoader, () -> implementation.initialize(montoya));

            //Registered after the implementation's handler, so the loader outlives the theme reset.
            montoya.extension().registerUnloadingHandler(this::release);
        } catch (Exception | LinkageError e) {
            release();
            throw new IllegalStateException("Burp Customizer failed to load", e);
        }
    }

    /**
     * Burp's application classloader. It defines Burp's FlatLaf and burp.theme classes, and the Montoya API
     * types shared with this class.
     */
    static ClassLoader hostClassLoader() {
        return BurpExtension.class.getClassLoader();
    }

    private static Path extractImplementation() throws IOException {
        Path jar = Files.createTempFile("burpcustomizer-", ".jar");
        jar.toFile().deleteOnExit();
        try (InputStream in = CustomizerBootstrap.class.getResourceAsStream("/" + IMPLEMENTATION_JAR)) {
            if (in == null) throw new IOException(IMPLEMENTATION_JAR + " is missing from the extension jar");
            Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        return jar;
    }

    private static void withContextClassLoader(ClassLoader loader, Runnable action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private void release() {
        //The implementation restores Burp's theme on the EDT; queue behind it before closing the loader.
        SwingUtilities.invokeLater(() -> {
            try {
                if (implementationLoader != null) implementationLoader.close();
                if (implementationJar != null) Files.deleteIfExists(implementationJar);
            } catch (IOException ignored) {
            } finally {
                implementationLoader = null;
                implementationJar = null;
            }
        });
    }
}
