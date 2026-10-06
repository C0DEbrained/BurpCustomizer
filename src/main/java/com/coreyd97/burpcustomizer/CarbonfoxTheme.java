package com.coreyd97.burpcustomizer;

import com.formdev.flatlaf.IntelliJTheme;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Very dark theme using the carbonfox palette from nightfox.nvim.
 */
public class CarbonfoxTheme extends IntelliJTheme.ThemeLaf implements BurpThemeOverrides {

    public static final String NAME = "Carbonfox";

    public CarbonfoxTheme() {
        super(loadTheme());
    }

    private static IntelliJTheme loadTheme() {
        try (InputStream in = CarbonfoxTheme.class.getResourceAsStream("themes/Carbonfox.theme.json")) {
            return new IntelliJTheme(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load Carbonfox theme", e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Properties getBurpOverrides() {
        Properties p = new Properties();

        //Monochrome palette, from base background (1) to strongest foreground (8).
        p.put("Colors.palette.mono.1", "#161616");
        p.put("Colors.palette.mono.2", "#252525");
        p.put("Colors.palette.mono.3", "#353535");
        p.put("Colors.palette.mono.4", "#484848");
        p.put("Colors.palette.mono.5", "#535353");
        p.put("Colors.palette.mono.6", "#7b7c7e");
        p.put("Colors.palette.mono.7", "#f2f4f8");
        p.put("Colors.palette.mono.8", "#f9fbff");
        p.put("Colors.palette.mono.core", "#0c0c0c");

        //Semantic colours used for text and icons.
        p.put("Colors.palette.error.core", "#ee5396");
        p.put("Colors.palette.error.4", "#f16da6");
        p.put("Colors.palette.error.5", "#ee5396");
        p.put("Colors.palette.success.core", "#25be6a");
        p.put("Colors.palette.success.4", "#25be6a");
        p.put("Colors.palette.info.core", "#33b1ff");
        p.put("Colors.palette.info.4", "#33b1ff");
        p.put("Colors.palette.hint.core", "#08bdba");
        p.put("Colors.palette.tertiary.core", "#be95ff");
        p.put("Colors.palette.tertiary.4", "#c8a5ff");
        p.put("Colors.palette.tertiary.5", "#be95ff");

        //Selected toggle buttons, e.g. Proxy "Intercept on": solid red so an active intercept is hard to miss.
        p.put("Button.selectedBackground", "#ee5396");
        p.put("Button.selectedBorderColor", "#ee5396");
        p.put("Button.hoverSelectedBorderColor", "#f16da6");
        p.put("Button.selectedForeground", "#161616");
        p.put("ToggleButton.selectedBackground", "#ee5396");
        p.put("ToggleButton.selectedForeground", "#161616");

        //Proxy history / Logger row highlights, in Burp's menu order: red, orange, yellow, green, cyan,
        //blue, pink, magenta, gray. Carbonfox has no orange or yellow, so those come from nightfox.
        String[] highlights = {"#ee5396", "#f4a261", "#dbc074", "#25be6a", "#33b1ff",
                "#78a9ff", "#ff7eb6", "#be95ff", "#7b7c7e"};
        for (int i = 0; i < highlights.length; i++) {
            p.put("Colors.ui.highlight." + i + ".background", highlights[i]);
            p.put("Colors.ui.highlight." + i + ".text", "#161616");
        }

        //HTTP message editor, matching the carbonfox syntax colours.
        String editor = "Colors.ui.editor.message.";
        p.put(editor + "background", "#161616");
        p.put(editor + "text", "#f2f4f8");
        p.put(editor + "currentLineBackground", "#252525");
        p.put(editor + "selectionBackground", "#33425c");
        p.put(editor + "gutterBorder", "#353535");
        p.put(editor + "lineNumbers", "#535353");
        p.put(editor + "lozengeBackground", "#353535");
        p.put(editor + "nestedLanguageBackground", "#7b7c7e1E");
        p.put(editor + "httpFirstLine", "#78a9ff");
        p.put(editor + "headerName", "#6690d9");
        p.put(editor + "headerValue", "#b6b8bb");
        p.put(editor + "paramName", "#08bdba");
        p.put(editor + "paramValue", "#25be6a");
        p.put(editor + "cookieName", "#be95ff");
        p.put(editor + "cookieValue", "#25be6a");
        p.put(editor + "tagName", "#6690d9");
        p.put(editor + "tagDelimiter", "#33b1ff");
        p.put(editor + "comment", "#535353");
        p.put(editor + "literalString", "#25be6a");
        p.put(editor + "literalQuote", "#25be6a");
        p.put(editor + "literalNumber", "#08bdba");
        p.put(editor + "literalBoolean", "#ee5396");
        p.put(editor + "reservedWord", "#be95ff");
        p.put(editor + "reservedWord2", "#be95ff");
        p.put(editor + "datatype", "#3ddbd9");
        p.put(editor + "function", "#6690d9");
        p.put(editor + "variable", "#ff7eb6");
        p.put(editor + "operator", "#33b1ff");
        p.put(editor + "separator", "#33b1ff");
        p.put(editor + "regex", "#3ddbd9");
        p.put(editor + "entityReference", "#3ddbd9");
        p.put(editor + "annotation", "#ff7eb6");
        p.put(editor + "preprocessor", "#ff7eb6");
        p.put(editor + "processingInstruction", "#ff7eb6");
        p.put(editor + "cdata", "#b6b8bb");
        p.put(editor + "cdataDelimiter", "#b6b8bb");
        return p;
    }
}
