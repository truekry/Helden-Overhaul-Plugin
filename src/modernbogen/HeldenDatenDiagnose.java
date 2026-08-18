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

/** Schreibt die von der Helden-Software über die Plugin-API gelieferten Heldendaten unverändert als XML. */
public final class HeldenDatenDiagnose {
    private HeldenDatenDiagnose() {}

    public static File export(Document doc, File file) throws Exception {
        if (doc == null) throw new IllegalArgumentException("Keine Heldendaten von der Helden-Software erhalten.");
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        try { transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); } catch (Exception ignored) { }

        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        } finally {
            writer.close();
        }
        return file;
    }
}
