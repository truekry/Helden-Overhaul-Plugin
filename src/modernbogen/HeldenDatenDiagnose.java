package modernbogen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Schreibt die über die Plugin-API gelieferten Heldendaten als eine auswertbare Diagnose-XML. */
public final class HeldenDatenDiagnose {
    private HeldenDatenDiagnose() {}

    /**
     * Schreibt normale Heldendaten und die zweite API-Antwort in eine gemeinsame XML-Datei.
     * So kann später eindeutig nachvollzogen werden, welche Daten die Software liefert.
     */
    public static File export(Document heldDoc, Document calculatedDoc, File file) throws Exception {
        if (heldDoc == null && calculatedDoc == null) {
            throw new IllegalArgumentException("Keine Heldendaten von der Helden-Software erhalten.");
        }

        Document source = heldDoc != null ? heldDoc : calculatedDoc;
        Document out = source.getImplementation().createDocument(null, "helden-software-diagnose", null);
        Element root = out.getDocumentElement();
        root.setAttribute("hinweis", "Diagnoseexport des Helden-Overhaul-Plugins");

        if (heldDoc != null) {
            Element normal = out.createElement("heldendaten_api");
            normal.setAttribute("action", "held");
            normal.setAttribute("id", "selected");
            normal.setAttribute("format", "xml");
            normal.appendChild(out.importNode(heldDoc.getDocumentElement(), true));
            root.appendChild(normal);
        }

        if (calculatedDoc != null) {
            Element calculated = out.createElement("berechnete_daten_api");
            calculated.setAttribute("action", "held");
            calculated.setAttribute("id", "selected");
            calculated.setAttribute("format", "xml");
            calculated.appendChild(out.importNode(calculatedDoc.getDocumentElement(), true));
            root.appendChild(calculated);
        }

        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        try {
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } catch (Exception ignored) { }

        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            transformer.transform(new DOMSource(out), new StreamResult(writer));
        } finally {
            writer.close();
        }
        return file;
    }

    /** Kompatibilitaet fuer Aufrufer, die nur eine XML-Antwort haben. */
    public static File export(Document doc, File file) throws Exception {
        return export(doc, null, file);
    }
}
