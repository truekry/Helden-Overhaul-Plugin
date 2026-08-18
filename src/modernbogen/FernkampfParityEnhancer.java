package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Ergänzt den Export um Fernkampfwaffen inklusive TP/Entfernung und FK-Talentwert. */
public final class FernkampfParityEnhancer {
    private FernkampfParityEnhancer() {}

    public static String enhance(String html, Document doc) {
        if (html == null || doc == null) return html;
        List<RangedWeapon> weapons = read(doc);
        if (weapons.isEmpty()) return html;

        String section = render(weapons);
        if (html.indexOf("<table class=\"fkwaffen modern-section\"") >= 0) return html;

        int marker = html.indexOf("<table class=\"ruestungen modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"schilde modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"inventar modern-section\"");
        if (marker < 0) marker = html.indexOf("<button type=\"button\" id=\"roll-log-toggle\"");
        if (marker < 0) return html;
        return html.substring(0, marker) + section + "\n" + html.substring(marker);
    }

    private static List<RangedWeapon> read(Document doc) {
        List<RangedWeapon> out = new ArrayList<RangedWeapon>();
        Set<String> seen = new HashSet<String>();
        collect(doc.getDocumentElement(), doc, out, seen);
        return out;
    }

    private static void collect(Node node, Document doc, List<RangedWeapon> out, Set<String> seen) {
        if (!(node instanceof Element)) return;
        Element e = (Element) node;
        String ln = localName(e).toLowerCase();
        if (isWeapon(ln)) {
            RangedWeapon w = parse(e, doc);
            if (!w.name.isEmpty()) {
                String key = w.name + "|" + w.tp + "|" + w.tpEntfernung + "|" + w.reichweite + "|" + w.fk + "|" + w.lz;
                if (seen.add(key)) out.add(w);
            }
        }
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) collect(children.item(i), doc, out, seen);
    }

    private static boolean isWeapon(String name) {
        return "fernkampfwaffe".equals(name)
                || "fkwaffe".equals(name)
                || "fernkampf".equals(name)
                || "distanzwaffe".equals(name)
                || name.endsWith("fernkampfwaffe");
    }

    private static RangedWeapon parse(Element e, Document doc) {
        RangedWeapon w = new RangedWeapon();
        w.name = first(e, "name", "waffenname", "bezeichnung", "bezeichner");
        w.typ = first(e, "typ", "talent", "waffentyp");
        w.tp = first(e, "tp", "trefferpunkte", "schaden");
        w.tpkk = first(e, "tpkk", "tp/kk");
        w.tpEntfernung = first(e,
                "tpentfernung", "tpEntfernung", "tp-entfernung", "tp_entfernung",
                "tpentf", "schadenentfernung", "entfernungtp");
        w.reichweite = first(e, "reichweite", "rw", "entfernung");
        w.lz = first(e, "ladezeit", "lz", "reload");

        // FK ist kein Waffenwert. Es ist der aktuelle Fernkampf-Talentwert
        // (z.B. Bogen-TaW) des Helden. Deshalb zuerst direkte Angaben prüfen
        // und anschließend den passenden Talent-Eintrag im gesamten XML suchen.
        w.fk = first(e, "fk", "fernkampfwert", "fkwert", "fernkampf");
        if (w.fk.isEmpty() && !w.typ.isEmpty()) {
            w.fk = findTalentValue(doc, w.typ);
        }
        if (w.fk.isEmpty()) {
            // Manche XML-Versionen speichern den Waffen-Talentnamen in einem
            // separaten Feld. Dann nochmals über bekannte Bezeichner suchen.
            String talentName = first(e, "talentname", "talentbezeichnung", "waffentalent");
            if (!talentName.isEmpty()) w.fk = findTalentValue(doc, talentName);
        }

        w.ini = first(e, "ini", "initiative");
        w.mod = first(e, "wm", "mod", "waffenmodifikator");
        w.munition = first(e, "munition", "munitionsart", "ammo");
        w.minbf = first(e, "minbf", "bfmin");
        w.aktbf = first(e, "aktbf", "bfakt", "bf");
        w.be = first(e, "be", "behinderung");
        return w;
    }

    /** Sucht den aktuellen TaW/FK-Wert des Fernkampftalents. */
    private static String findTalentValue(Document doc, String talentName) {
        if (doc == null || talentName == null || talentName.trim().isEmpty()) return "";
        String wanted = normalize(talentName);
        NodeList all = doc.getElementsByTagName("talent");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element t = (Element) n;
            String name = first(t, "name", "bezeichnung", "bezeichner");
            if (!normalize(name).equals(wanted)) continue;
            String value = first(t, "wert", "taw", "taW", "value", "punkte", "punktewert", "aktuell");
            if (!value.isEmpty()) return value;
        }

        // Fallback für XML-Strukturen, in denen Talent nicht als <talent>,
        // sondern unter einer Liste/Gruppe abgelegt ist.
        return findTalentValueRecursive(doc.getDocumentElement(), wanted);
    }

    private static String findTalentValueRecursive(Node node, String wanted) {
        if (!(node instanceof Element)) return "";
        Element e = (Element) node;
        String nodeName = localName(e).toLowerCase();
        if (nodeName.contains("talent") || nodeName.contains("fernkampf")) {
            String name = first(e, "name", "bezeichnung", "bezeichner", "talentname");
            if (normalize(name).equals(wanted)) {
                String value = first(e, "wert", "taw", "value", "punkte", "punktewert", "aktuell");
                if (!value.isEmpty()) return value;
            }
        }
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            String result = findTalentValueRecursive(children.item(i), wanted);
            if (!result.isEmpty()) return result;
        }
        return "";
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String render(List<RangedWeapon> weapons) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"fkwaffen modern-section\" id=\"section-fernkampfwaffen-10\">");
        sb.append("<tr><th class=\"titel\" colspan=\"13\">Fernkampfwaffen</th></tr>");
        sb.append("<tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"fkwaffen gitternetz\"><thead><tr>");
        sb.append("<th class=\"name\">Fernkampfwaffe</th>");
        sb.append("<th class=\"typ\">Typ</th><th class=\"tp\">TP</th><th class=\"tpkk\">TP/KK</th>");
        sb.append("<th class=\"tp-entfernung\">TP/Entfernung</th>");
        sb.append("<th class=\"reichweite\">Reichweite</th><th class=\"lz\">LZ</th><th class=\"fk\">FK</th>");
        sb.append("<th class=\"ini\">INI</th><th class=\"wm\">WM</th><th class=\"munition\">Munition</th>");
        sb.append("<th class=\"minbf\">min BF</th><th class=\"aktbf\">akt BF</th></tr></thead><tbody>");
        for (RangedWeapon w : weapons) {
            sb.append("<tr>");
            cell(sb, "name", w.name); cell(sb, "typ", w.typ); cell(sb, "tp", w.tp); cell(sb, "tpkk", w.tpkk);
            cell(sb, "tp-entfernung", w.tpEntfernung);
            cell(sb, "reichweite", w.reichweite); cell(sb, "lz", w.lz); cell(sb, "fk", w.fk);
            cell(sb, "ini", w.ini); cell(sb, "wm", w.mod); cell(sb, "munition", w.munition);
            cell(sb, "minbf", w.minbf); cell(sb, "aktbf", w.aktbf);
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div></td></tr></table>");
        return sb.toString();
    }

    private static void cell(StringBuilder sb, String cls, String value) {
        sb.append("<td class=\"").append(cls).append("\">").append(esc(value)).append("</td>");
    }

    private static String first(Element e, String... keys) {
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String value = textRecursive(e, key);
            if (!value.isEmpty()) return value;
            value = attrIgnoreCase(e, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String attrIgnoreCase(Element e, String key) {
        if (e.hasAttribute(key)) return e.getAttribute(key).trim();
        for (int i = 0; i < e.getAttributes().getLength(); i++) {
            Node n = e.getAttributes().item(i);
            if (n.getNodeName().equalsIgnoreCase(key)) return n.getNodeValue().trim();
        }
        return "";
    }

    private static String textRecursive(Element e, String key) {
        NodeList nodes = e.getElementsByTagNameNS("*", key);
        if (nodes.getLength() == 0) nodes = e.getElementsByTagName(key);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent().trim();
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && localName(n).equalsIgnoreCase(key)) return n.getTextContent().trim();
        }
        return "";
    }

    private static String localName(Node n) {
        String s = n.getLocalName();
        if (s != null && !s.isEmpty()) return s;
        String q = n.getNodeName();
        int colon = q.indexOf(':');
        return colon >= 0 ? q.substring(colon + 1) : q;
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }

    private static final class RangedWeapon {
        String name = "", typ = "", tp = "", tpkk = "", tpEntfernung = "", reichweite = "", lz = "";
        String fk = "", ini = "", mod = "", munition = "", minbf = "", aktbf = "", be = "";
    }
}
