package modernbogen;

import helden.plugin.HeldenXMLDatenPlugin3;
import helden.plugin.datenxmlplugin.DatenAustausch3Interface;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Helden-Overhaul-Plugin mit XML- und nativer HTML-Datenquelle. */
public class ModernBogenPlugin implements HeldenXMLDatenPlugin3 {
    private DatenAustausch3Interface dai;
    private JFrame frame;

    public ModernBogenPlugin() { super(); }
    @Override public String getMenuName() { return "Helden-Overhaul"; }
    @Override public String getToolTipText() { return "Erzeugt einen modernen HTML-Charakterbogen (Fantasy-Layout, Würfel, Dark Mode)"; }
    @Override public ImageIcon getIcon() { return null; }
    @Override public void doWork(JFrame f) { }
    @Override public String getType() { return DATEN; }
    @Override public void init(DatenAustausch3Interface d, JFrame f) { dai = d; frame = f; }
    @Override public boolean hatMenu() { return true; }
    @Override public boolean hatTab() { return false; }
    @Override public JComponent getPanel() { return null; }
    @Override public void click() { exportModernBogen(); }

    @Override public ArrayList<JComponent> getUntermenus() {
        ArrayList<JComponent> liste = new ArrayList<JComponent>();
        JMenuItem export = new JMenuItem("HTML exportieren");
        export.addActionListener(new ActionListener() { @Override public void actionPerformed(ActionEvent e) { exportModernBogen(); } });
        liste.add(export);
        return liste;
    }

    private File exportModernBogen() {
        Document heldDoc = getCurrentHeldenXml();
        if (heldDoc == null) {
            JOptionPane.showMessageDialog(frame, "Kein Held geladen oder XML konnte nicht gelesen werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // Die native HTML-Ausgabe ist die bevorzugte Quelle für Waffenwerte.
        // Genau diese Daten verwendet auch das Python-Original als Grundlage.
        String nativeHtml = getCurrentHeldenHtml();

        String heldName = HtmlGenerator.extractHeldName(heldDoc);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Helden-Overhaul: HTML exportieren");
        chooser.setSelectedFile(new File(sanitizeFilename(heldName) + "_modern.html"));
        chooser.setFileFilter(new FileNameExtensionFilter("HTML-Dateien", "html", "htm"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return null;

        File htmlFile = chooser.getSelectedFile();
        if (!htmlFile.getName().toLowerCase().endsWith(".html") && !htmlFile.getName().toLowerCase().endsWith(".htm")) {
            htmlFile = new File(htmlFile.getParentFile(), htmlFile.getName() + ".html");
        }

        try {
            String html = HtmlGenerator.generate(heldDoc);
            html = HtmlParityEnhancer.enhance(html, heldDoc);
            html = FernkampfParityEnhancer.enhance(html, heldDoc, nativeHtml);
            Writer w = new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8);
            try { w.write(html); } finally { w.close(); }
            JOptionPane.showMessageDialog(frame, "Gespeichert:\n" + htmlFile.getAbsolutePath(), "Fertig", JOptionPane.INFORMATION_MESSAGE);
            return htmlFile;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Fehler beim Speichern:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private Document getCurrentHeldenXml() {
        if (dai == null) return null;
        String[][] variants = new String[][] {
            {"held", "selected", "xml", "3"},
            {"held", "selected", "xml", "2"},
            {"held", "selected", "xml", "1"},
            {"held", "selected", "xml", ""},
            {"held", "active", "xml", "3"}
        };
        Document best = null;
        int bestScore = -1;
        for (int i = 0; i < variants.length; i++) {
            try {
                Document request = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                Element action = request.createElement("action");
                request.appendChild(action);
                action.setAttribute("action", variants[i][0]);
                action.setAttribute("id", variants[i][1]);
                action.setAttribute("format", variants[i][2]);
                if (variants[i][3].length() > 0) action.setAttribute("version", variants[i][3]);
                Object result = dai.exec(request);
                if (!(result instanceof Document)) continue;
                Document doc = (Document) result;
                int score = doc.getElementsByTagName("zauber").getLength() * 10
                        + doc.getElementsByTagName("zauberliste").getLength() * 5
                        + doc.getElementsByTagName("talent").getLength()
                        + doc.getElementsByTagName("talentliste").getLength();
                if (doc.getDocumentElement() != null && "daten".equalsIgnoreCase(doc.getDocumentElement().getTagName())) score += 2;
                if (score > bestScore) { bestScore = score; best = doc; }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
        return best;
    }

    /**
     * Fragt die Helden-Software direkt nach ihrer nativen HTML-Ausgabe.
     * Die dokumentierte/erprobte XML-Schnittstelle verwendet eine action-
     * Anfrage; HTML wird deshalb über dieselbe held/selected-Operation mit
     * format="html" probiert. Mehrere Versionsvarianten machen das Plugin
     * kompatibel zu unterschiedlichen Helden-Software-Versionen.
     */
    private String getCurrentHeldenHtml() {
        if (dai == null) return null;
        String[][] variants = new String[][] {
            {"held", "selected", "html", "3"},
            {"held", "selected", "html", "2"},
            {"held", "selected", "html", "1"},
            {"held", "selected", "html", ""},
            {"held", "active", "html", "3"}
        };

        for (int i = 0; i < variants.length; i++) {
            try {
                Document request = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                Element action = request.createElement("action");
                request.appendChild(action);
                action.setAttribute("action", variants[i][0]);
                action.setAttribute("id", variants[i][1]);
                action.setAttribute("format", variants[i][2]);
                if (variants[i][3].length() > 0) action.setAttribute("version", variants[i][3]);

                Object result = dai.exec(request);
                String html = extractHtmlResult(result);
                if (html != null && looksLikeCharacterHtml(html)) return html;
            } catch (Exception ex) {
                // Eine nicht unterstützte Variante ist erwartbar; mit der
                // nächsten API-Version fortfahren.
            }
        }
        return null;
    }

    private static String extractHtmlResult(Object result) {
        if (result == null) return null;
        if (result instanceof String) return (String) result;
        if (result instanceof byte[]) return new String((byte[]) result, StandardCharsets.UTF_8);
        if (result instanceof Document) {
            Document d = (Document) result;
            Element root = d.getDocumentElement();
            if (root == null) return null;
            String text = root.getTextContent();
            if (text != null && (text.contains("<html") || text.contains("<table") || text.contains("Fernkampf"))) return text;
        }
        return null;
    }

    private static boolean looksLikeCharacterHtml(String html) {
        String s = html.toLowerCase();
        return s.contains("<html") || (s.contains("<table") && (s.contains("waffe") || s.contains("talent")));
    }

    private static void copyResource(String resourceName, File target) throws Exception {
        InputStream in = ModernBogenPlugin.class.getResourceAsStream("/" + resourceName);
        if (in == null) in = ModernBogenPlugin.class.getResourceAsStream("/resources/" + resourceName);
        if (in == null) in = ModernBogenPlugin.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) throw new IllegalStateException("Ressource nicht gefunden: " + resourceName);
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            } finally { out.close(); }
        } finally { in.close(); }
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) return "Held";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
