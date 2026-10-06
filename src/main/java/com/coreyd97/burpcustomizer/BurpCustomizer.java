package com.coreyd97.burpcustomizer;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class BurpCustomizer implements BurpExtension {

    enum ThemeSource {BUILTIN, FILE}

    private LookAndFeel originalBurpTheme;
    @Getter
    private ArrayList<UIManager.LookAndFeelInfo> themes;
    @Getter
    private UIManager.LookAndFeelInfo selectedBuiltIn;
    @Getter
    private File selectedThemeFile;
    @Getter
    private ThemeSource themeSource;
    private CustomizerPanel ui;
    public static MontoyaApi montoya;
    JMenuBar menuBar;
    JMenu menuItem;

    public BurpCustomizer() {
        themes = (ArrayList<UIManager.LookAndFeelInfo>) Arrays.asList(FlatAllIJThemes.INFOS).stream()
                .filter(lookAndFeelInfo -> !lookAndFeelInfo.getName().equalsIgnoreCase("Xcode-Dark"))
                .map(flatIJLookAndFeelInfo -> (UIManager.LookAndFeelInfo) flatIJLookAndFeelInfo)
                        .collect(Collectors.toList());
        themes.sort(Comparator.comparing(UIManager.LookAndFeelInfo::getName));
    }

    @SneakyThrows
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        BurpCustomizer.montoya = montoyaApi;
        originalBurpTheme = UIManager.getLookAndFeel();

        String sourceEnum = montoya.persistence().preferences().getString("source");
        if (sourceEnum == null || sourceEnum.equalsIgnoreCase("")) {
            themeSource = ThemeSource.BUILTIN;
        } else {
            themeSource = ThemeSource.valueOf(sourceEnum);
        }

        String builtIn = montoya.persistence().preferences().getString("theme");
        Optional<UIManager.LookAndFeelInfo> previousTheme =
                themes.stream().filter(lookAndFeelInfo -> lookAndFeelInfo.getClassName().equalsIgnoreCase(builtIn)).findFirst();
        previousTheme.ifPresent(lookAndFeelInfo -> selectedBuiltIn = lookAndFeelInfo);

        String themeFilePref = montoya.persistence().preferences().getString("themeFile");
        if (themeFilePref != null && !themeFilePref.equalsIgnoreCase("")) {
            selectedThemeFile = new File(themeFilePref);
            if (!selectedThemeFile.exists()) selectedThemeFile = null;
        }

        //Debugging aids only, never let them prevent the extension from loading.
        try {
            FlatUIDefaultsInspector.install("ctrl shift alt Y");
            FlatInspector.install("ctrl shift alt U");
            patchPopupFactoryForFlatInspector();
        } catch (Throwable e) {
            montoya.logging().logToError("Could not install FlatLaf inspectors: " + e);
        }

//            Arrays.stream(Frame.getFrames()).filter(frame -> frame.getTitle().startsWith("Burp Suite") && frame.isVisible() && frame.getMenuBar() != null).findFirst().ifPresent(frame -> {
//                menuItem = new JMenu("Customize");
//                menuItem.addMouseListener(new MouseAdapter() {
//                    @Override
//                    public void mouseClicked(MouseEvent e) {
//                        setTheme(selectedTheme);
//                    }
//                });
//                menuBar = ((JFrame) frame).getJMenuBar();
//                if(menuBar != null) {
//                    menuBar.add(menuItem);
//                }
//            });

        this.ui = new CustomizerPanel(this);
        montoya.extension().registerUnloadingHandler(this::extensionUnloaded);

        SwingUtilities.invokeLater(() -> {
            if (themeSource == ThemeSource.BUILTIN && selectedBuiltIn != null) {
                setTheme(selectedBuiltIn);
            } else if (themeSource == ThemeSource.FILE && selectedThemeFile != null) {
                setTheme(selectedThemeFile);
            }

            montoya.userInterface().registerSuiteTab("Customizer", this.ui);
        });
    }

    //Since Burp explicitly disables HTML in components we need to manually re-enable HTML for the inspector tooltip.
    //Shouldn't reintroduce any vulnerabilities unless somehow a malicious value is used in a tooltip somewhere which is unlikely
    private void patchPopupFactoryForFlatInspector() {
        PopupFactory.setSharedInstance(new PopupFactory() {
            @Override
            public Popup getPopup(Component owner, Component contents, int x, int y) throws IllegalArgumentException {
                if (contents instanceof JToolTip) {
                    ((JToolTip) contents).putClientProperty("html.disable", false);
                }
                return super.getPopup(owner, contents, x, y);
            }
        });
    }

    public void setTheme(UIManager.LookAndFeelInfo lookAndFeelInfo) {
        try {
            LookAndFeel laf = createThemeFromDefaults(lookAndFeelInfo, false);
            UIManager.setLookAndFeel(laf);
            FlatLaf.updateUI();
            patchPopupFactoryForFlatInspector();
            selectedBuiltIn = lookAndFeelInfo;
            montoya.persistence().preferences().setString("theme", lookAndFeelInfo.getClassName());
            montoya.persistence().preferences().setString("source", ThemeSource.BUILTIN.toString());

//            ui.reloadPreview();
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            montoya.logging().logToError("Could not load theme.");
            montoya.logging().logToError(sw.toString());
            JOptionPane.showMessageDialog(ui, "Could not load the specified theme.\n" + ex.getMessage(), "Burp Customizer", JOptionPane.ERROR_MESSAGE);
            try { //Fall back to built in theme if we encounter an issue.
                UIManager.setLookAndFeel(originalBurpTheme);
            } catch (Exception ignored) {
            }
        }
    }

    public LookAndFeel createThemeFromDefaults(UIManager.LookAndFeelInfo lookAndFeelInfo, boolean isPreview) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class themeClass = Class.forName(lookAndFeelInfo.getClassName());
        IntelliJTheme.ThemeLaf theme = (IntelliJTheme.ThemeLaf) themeClass.getDeclaredConstructor().newInstance();
        return new CustomTheme(theme, isPreview);
    }

    public void setTheme(File themeJsonFile) {
        try {
            LookAndFeel lookAndFeel = createThemeFromFile(themeJsonFile);
            UIManager.setLookAndFeel(lookAndFeel);
            FlatLaf.updateUI();

            selectedThemeFile = themeJsonFile;
            montoya.persistence().preferences().setString("themeFile", themeJsonFile.getAbsolutePath());
            montoya.persistence().preferences().setString("source", ThemeSource.FILE.toString());
        } catch (IOException | UnsupportedLookAndFeelException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            montoya.logging().logToError("Could not load theme.");
            montoya.logging().logToError(sw.toString());
            JOptionPane.showMessageDialog(ui, "Could not load the specified theme:\n" + ex.getMessage(), "Burp Customizer", JOptionPane.ERROR_MESSAGE);
            try { //Fall back to built in theme if we encounter an issue.
                UIManager.setLookAndFeel(originalBurpTheme);
            } catch (Exception ignored) {
            }
        }
    }

    public LookAndFeel createThemeFromFile(File themeJsonFile) throws IOException, UnsupportedLookAndFeelException {
        IntelliJTheme intelliJTheme = new IntelliJTheme(new FileInputStream(themeJsonFile));
        IntelliJTheme.ThemeLaf fileTheme = new IntelliJTheme.ThemeLaf(intelliJTheme);
        if (intelliJTheme.name == null && intelliJTheme.author == null) {
            throw new UnsupportedLookAndFeelException(themeJsonFile.getName() + " does not appear to be a valid theme file.\n" +
                    "If it is, make sure it has json attributes \"name\" and \"author\".");
        }

        return new CustomTheme(fileTheme, false);
    }

    public void extensionUnloaded() {
        BurpCustomizer.montoya = null;
        if (menuBar != null && menuItem != null) menuBar.remove(menuItem);
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(originalBurpTheme);
                FlatLaf.updateUI();
            } catch (UnsupportedLookAndFeelException e) {
            }
        });
    }
}
