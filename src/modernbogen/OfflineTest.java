package modernbogen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

/**
 * Erzeugt einen modernen Bogen aus einer .xml-Datei ohne Helden-Software.
 * Aufruf: java modernbogen.OfflineTest Held.xml Ausgabe.html
 */
public final class OfflineTest {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: OfflineTest <held.xml> <out.html>");
            System.exit(1);
        }
        File xmlFile = new File(args[0]);
        File htmlFile = new File(args[1]);

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xmlFile);

        String html = HtmlGenerator.generate(doc);
        Writer w = new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8);
        try {
            w.write(html);
        } finally {
            w.close();
        }

        // CSS neben HTML kopieren (aus resources oder Sibling)
        File cssOut = new File(htmlFile.getParentFile(), "heldenstyle.css");
        File cssSrc = new File("resources/heldenstyle.css");
        if (!cssSrc.isFile()) {
            cssSrc = new File("heldenstyle.css");
        }
        if (cssSrc.isFile()) {
            Files.copy(cssSrc.toPath(), cssOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("OK: " + htmlFile.getAbsolutePath());
        System.out.println("CSS: " + cssOut.getAbsolutePath());
        System.out.println("Held: " + HtmlGenerator.extractHeldName(doc));
    }
}
