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

/** Helden-Software-Plugin: erzeugt einen modernen HTML-Charakterbogen. */
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
        export.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { exportModernBogen(); }
        });
        liste.add(export);
        return liste;
    }

    /**
     * Holt den aktuell ausgewählten Helden genau einmal aus der Plugin-API.
     * Die Antwort ist die zentrale Datenquelle für Generator und alle Enhancer.
     * Dadurch entfallen doppelte API-Aufrufe und die bisherige künstliche
     * Trennung zwischen "normalen" und "berechneten" Heldendaten.
     */
    private File exportModernBogen() {
        Document heldDoc = getCurrentHeldenXml();
        if (heldDoc == null) {
            JOptionPane.showMessageDialog(frame, "Kein Held geladen oder XML konnte nicht gelesen werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }

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
            html = FernkampfParityEnhancer.enhance(html, heldDoc);

            Writer w = new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8);
            try {
                w.write(html);
            } finally {
                w.close();
            }

            JOptionPane.showMessageDialog(frame, "Gespeichert:\n" + htmlFile.getAbsolutePath(), "Fertig", JOptionPane.INFORMATION_MESSAGE);
            return htmlFile;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Fehler beim Speichern:\n" + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Holt die vollständige XML-Antwort des aktuell ausgewählten Helden. */
    private Document getCurrentHeldenXml() {
        if (dai == null) return null;
        try {
            Document request = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element action = request.createElement("action");
            request.appendChild(action);
            action.setAttribute("action", "held");
            action.setAttribute("id", "selected");
            action.setAttribute("format", "xml");

            Object result = dai.exec(request);
            return result instanceof Document ? (Document) result : null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static void copyResource(String resourceName, File target) throws Exception {
        InputStream in = ModernBogenPlugin.class.getResourceAsStream("/" + resourceName);
        if (in == null) in = ModernBogenPlugin.class.getResourceAsStream("/resources/" + resourceName);
        if (in == null) in = ModernBogenPlugin.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) throw new IllegalStateException("Ressource nicht gefunden: " + resourceName);
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            } finally { out.close(); }
        } finally { in.close(); }
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) return "Held";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
