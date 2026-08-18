package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Ergänzt den von HtmlGenerator erzeugten Bogen um Darstellungen, die der
 * Python-Overhaul aus dem ursprünglichen HTML erhält bzw. modernisiert.
 *
 * Der wichtigste Punkt ist die Zonenrüstung: Die XML-Strukturen der
 * Helden-Software unterscheiden sich je nach Export/API-Version. Deshalb
 * werden Rüstungen rekursiv gesucht und sowohl Text-Elemente als auch
 * Attribute berücksichtigt.
 */
public final class HtmlParityEnhancer {
    private HtmlParityEnhancer() {}

    public static String enhance(String html, Document doc) {
        if (html == null || doc == null) return html;

        List<Armor> armors = readArmors(doc);
        if (armors.isEmpty()) return html;

        String section = renderArmorSection(armors);

        // HtmlGenerator erzeugt bereits eine Rüstungssektion. Wir ersetzen sie
        // komplett, damit auch ältere/alternative XML-Formate dieselbe
        // Zonen-Tabelle erhalten.
        int start = html.indexOf("<table class=\"ruestungen modern-section\"");
        if (start >= 0) {
            int end = findMatchingTableEnd(html, start);
            if (end >= 0) {
                return html.substring(0, start) + section + html.substring(end);
            }
        }

        // Falls HtmlGenerator mangels Daten keine Rüstungssektion erzeugt hat,
        // direkt vor Schilden bzw. Inventar einfügen.
        int marker = html.indexOf("<table class=\"schilde modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"inventar modern-section\"");
        if (marker < 0) marker = html.indexOf("<button type=\"button\" id=\"roll-log-toggle\"");
        if (marker >= 0) {
            return html.substring(0, marker) + section + "\n" + html.substring(marker);
        }
        return html;
    }

    /** Findet das zu einer äußeren <table> gehörende </table>. */
    private static int findMatchingTableEnd(String html, int start) {
        int depth = 0;
        int pos = start;
        while (pos < html.length()) {
            int open = html.indexOf("<table", pos);
            int close = html.indexOf("</table>", pos);
            if (close < 0) return -1;
            if (open >= 0 && open < close) {
                depth++;
                pos = open + 6;
            } else {
                depth--;
                int end = close + "</table>".length();
                if (depth == 0) return end;
                pos = end;
            }
        }
        return -1;
    }

    private static List<Armor> readArmors(Document doc) {
        List<Armor> result = new ArrayList<Armor>();
        Set<String> seen = new HashSet<String>();
        collectArmorNodes(doc.getDocumentElement(), result, seen);

        // Einige API-Varianten verwenden explizit ruestungeinfach.
        NodeList simple = doc.getElementsByTagNameNS("*", "ruestungeinfach");
        if (simple.getLength() == 0) simple = doc.getElementsByTagName("ruestungeinfach");
        for (int i = 0; i < simple.getLength(); i++) {
            if (simple.item(i) instanceof Element) addArmor((Element) simple.item(i), result, seen);
        }
        return result;
    }

    private static void collectArmorNodes(Node node, List<Armor> result, Set<String> seen) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        String name = localName(el).toLowerCase();
        if ("ruestung".equals(name) || "ruestungeinfach".equals(name) || name.endsWith("ruestung")) {
            addArmor(el, result, seen);
        }
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectArmorNodes(children.item(i), result, seen);
        }
    }

    private static void addArmor(Element el, List<Armor> result, Set<String> seen) {
        Armor a = parseArmor(el);
        if (a.name.isEmpty()) return;

        String key = a.name + "|" + a.kopf + "|" + a.brust + "|" + a.ruecken + "|"
                + a.bauch + "|" + a.linkerArm + "|" + a.rechterArm + "|"
                + a.linkesBein + "|" + a.rechtesBein + "|" + a.rs + "|" + a.be;
        if (seen.add(key)) result.add(a);
    }

    private static Armor parseArmor(Element el) {
        Armor a = new Armor();
        a.name = first(el, "name", "bezeichnung", "bezeichner");
        a.kopf = first(el, "kopf", "kopfschutz", "ko");
        a.brust = first(el, "brust", "brustschutz", "br");
        a.ruecken = first(el, "ruecken", "rücken", "rueckenschutz", "rue");
        a.bauch = first(el, "bauch", "bauchschutz", "ba");
        a.linkerArm = first(el, "linkerarm", "linkerArm", "la");
        a.rechterArm = first(el, "rechterarm", "rechterArm", "ra");
        a.linkesBein = first(el, "linkesbein", "linkesBein", "lb");
        a.rechtesBein = first(el, "rechtesbein", "rechtesBein", "rb");
        a.gesamt = first(el, "gesamt", "ges");
        a.grs = first(el, "gesamtzonenschutz", "gesamtzonenschutzwert", "grs", "gers");
        a.gbe = first(el, "gesamtbehinderung", "gesamt-be", "gbe");
        a.rs = first(el, "rs", "ruestungsschutz", "schutz");
        a.be = first(el, "be", "behinderung");

        // Manche Exporte speichern Gesamtwerte unter leicht anderen Namen.
        if (a.rs.isEmpty()) a.rs = first(el, "gesamtzonenschutz", "grs", "rs");
        if (a.grs.isEmpty()) a.grs = first(el, "gesamtzonenschutz", "grs", "gers");
        if (a.be.isEmpty()) a.be = first(el, "behinderung", "be", "gbe");
        if (a.gbe.isEmpty()) a.gbe = first(el, "behinderung", "gbe", "be");
        return a;
    }

    private static String renderArmorSection(List<Armor> armors) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"ruestungen modern-section\" id=\"section-ruestungen-11\">");
        sb.append("<tr><th class=\"titel\" colspan=\"14\">Rüstungen</th></tr>");
        sb.append("<tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"zonenruestungen gitternetz\"><thead><tr>");
        sb.append("<th class=\"name\">Name</th>");
        sb.append("<th class=\"ko\">Ko</th><th class=\"br\">Br</th><th class=\"rue\">Rü</th><th class=\"ba\">Ba</th>");
        sb.append("<th class=\"la\">LA</th><th class=\"ra\">RA</th><th class=\"lb\">LB</th><th class=\"rb\">RB</th>");
        sb.append("<th class=\"ges\">Ges</th><th class=\"grs\">gRS</th><th class=\"gbe\">gBE</th>");
        sb.append("<th class=\"rs\">RS</th><th class=\"be\">BE</th></tr></thead><tbody>");

        String totalBe = "";
        String totalGrs = "";
        String totalGbe = "";
        String totalRs = "";
        for (Armor a : armors) {
            sb.append("<tr>");
            cell(sb, "name", a.name); cell(sb, "ko", a.kopf); cell(sb, "br", a.brust);
            cell(sb, "rue", a.ruecken); cell(sb, "ba", a.bauch); cell(sb, "la", a.linkerArm);
            cell(sb, "ra", a.rechterArm); cell(sb, "lb", a.linkesBein); cell(sb, "rb", a.rechtesBein);
            cell(sb, "ges", a.gesamt); cell(sb, "grs", a.grs); cell(sb, "gbe", a.gbe);
            cell(sb, "rs", a.rs); cell(sb, "be", a.be);
            sb.append("</tr>");
            if (!a.be.isEmpty()) totalBe = a.be;
            if (!a.grs.isEmpty()) totalGrs = a.grs;
            if (!a.gbe.isEmpty()) totalGbe = a.gbe;
            if (!a.rs.isEmpty()) totalRs = a.rs;
        }
        sb.append("<tr><td class=\"name\">Gesamt</td>");
        sb.append("<td class=\"ko\"></td><td class=\"br\"></td><td class=\"rue\"></td><td class=\"ba\"></td>");
        sb.append("<td class=\"la\"></td><td class=\"ra\"></td><td class=\"lb\"></td><td class=\"rb\"></td><td class=\"ges\"></td>");
        cell(sb, "grs", totalGrs); cell(sb, "gbe", totalGbe.isEmpty() ? totalBe : totalGbe);
        cell(sb, "rs", totalRs); cell(sb, "be", totalBe);
        sb.append("</tr></tbody></table></div></td></tr></table>");
        return sb.toString();
    }

    private static void cell(StringBuilder sb, String cls, String value) {
        sb.append("<td class=\"").append(cls).append("\">").append(esc(value)).append("</td>");
    }

    /** Liest zuerst ein gleichnamiges XML-Element und danach ein gleichnamiges Attribut. */
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
            if (!(n instanceof Element)) continue;
            Element child = (Element) n;
            if (localName(child).equalsIgnoreCase(key)) return child.getTextContent().trim();
        }
        return "";
    }

    private static String localName(Node n) {
        String s = n.getLocalName();
        return s != null ? s : n.getNodeName();
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }

    private static final class Armor {
        String name = "", kopf = "", brust = "", ruecken = "", bauch = "";
        String linkerArm = "", rechterArm = "", linkesBein = "", rechtesBein = "";
        String gesamt = "", grs = "", gbe = "", rs = "", be = "";
    }
}
