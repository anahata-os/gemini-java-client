/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing.render;

import com.google.genai.types.Part;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import uno.anahata.ai.swing.render.editorkit.EditorKitProvider;

public class TextPartRenderer implements PartRenderer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\r?\\n([\\s\\S]*?)\\r?\\n```");
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public TextPartRenderer() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, "<br />");
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @Override
    public JComponent render(Part part, EditorKitProvider editorKitProvider) {
        String markdownText = part.text().orElse("");
        boolean isThought = part.thought().orElse(false);
        
        if (!CODE_BLOCK_PATTERN.matcher(markdownText).find()) {
            return createHtmlPane(htmlRenderer.render(markdownParser.parse(markdownText)), isThought);
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(markdownText);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String textSegment = markdownText.substring(lastEnd, matcher.start());
                panel.add(createHtmlPane(htmlRenderer.render(markdownParser.parse(textSegment)), isThought));
            }
            String language = matcher.group(1);
            String code = matcher.group(2);
            JComponent codeBlock = CodeBlockRenderer.render(language, code, editorKitProvider);
            codeBlock.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            panel.add(codeBlock);
            lastEnd = matcher.end();
        }

        if (lastEnd < markdownText.length()) {
            String textSegment = markdownText.substring(lastEnd);
            panel.add(createHtmlPane(htmlRenderer.render(markdownParser.parse(textSegment)), isThought));
        }
        return panel;
    }

    private JComponent createHtmlPane(String html, boolean isThought) {
        JEditorPane editorPane = new ContentRenderer.WrappingEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setOpaque(false); 
        
        HTMLEditorKit kit = new HTMLEditorKit();
        
        // Create a local stylesheet to avoid polluting the global HTMLEditorKit stylesheet
        StyleSheet sheet = new StyleSheet();
        sheet.addStyleSheet(kit.getStyleSheet());
        
        String bodyRule = "body { word-wrap: break-word; font-family: sans-serif; font-size: 14px; background-color: transparent; ";
        if (isThought) {
            bodyRule += "font-style: italic; color: #777777; ";
        }
        bodyRule += "}";
        
        sheet.addRule(bodyRule);
        sheet.addRule("table { border-collapse: collapse; width: 100%; }");
        sheet.addRule("th, td { border: 1px solid #dddddd; text-align: left; padding: 8px; }");
        sheet.addRule("th { background-color: #f2f2f2; }");
        
        // Correct way to set a per-instance stylesheet: pass it to the HTMLDocument constructor
        HTMLDocument doc = new HTMLDocument(sheet);
        editorPane.setEditorKit(kit);
        editorPane.setDocument(doc);
        
        editorPane.setText("<html><body>" + html + "</body></html>");
        editorPane.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        
        // FIX: Wrap the editor pane in a JPanel with BorderLayout. This is a standard
        // Swing trick to force the child component (the editor pane) to respect the
        // width of its container, which enables line wrapping.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(editorPane, BorderLayout.CENTER);
        return wrapper;
    }
}