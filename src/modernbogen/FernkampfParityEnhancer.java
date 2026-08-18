package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Erzeugt den Fernkampfbereich direkt aus der vollständigen Held-XML der Helden-Software. */
public final class FernkampfParityEnhancer {
    private FernkampfParityEnhancer() {}

    public static String enhance(String html, Document heldDoc) {
        if (html == null || heldDoc == null) return html;

        // Die zentrale Held-XML enthält bereits die von der Helden-Software
        // berechneten Kampfsets. Deshalb wird hier keine zweite API-Antwort benötigt.
        List<RangedWeapon> weapons = readEquipped(heldDoc);
        mergeCalculatedWeapons(weapons, heldDoc);
        if (weapons.isEmpty()) return html;

        String section = render(weapons);
        String oldMarker = "<table class=\"fkwaffen modern-section\"";
        int oldStart = html.indexOf(oldMarker);
        if (oldStart >= 0) {
            int oldEnd = html.indexOf("</table>", oldStart);
            if (oldEnd >= 0) {
                oldEnd += "</table>".length();
                return html.substring(0, oldStart) + section + html.substring(oldEnd);
            }
        }

        int marker = html.indexOf("<table class=\"ruestungen modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"schilde modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"inventar modern-section\"");
        if (marker < 0) {
            int bodyEnd = html.lastIndexOf("</body>");
            if (bodyEnd >= 0) return html.substring(0, bodyEnd) + section + "\n" + html.substring(bodyEnd);
            return html + section;
        }
        return html.substring(0, marker) + section + "\n" + html.substring(marker);
    }

    private static List<RangedWeapon> readEquipped(Document doc) {
        List<RangedWeapon> result = new ArrayList<RangedWeapon>();
        Set<String> seen = new HashSet<String>();
        NodeList equipped = doc.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < equipped.getLength(); i++) {
            Element e = (Element) equipped.item(i);
            String id = attr(e, "name");
            if (!id.toLowerCase().startsWith("fkwaffe")) continue;
            String weaponName = attr(e, "waffenname", "name");
            String talent = attr(e, "talent", "kampftalent");
            if (talent.isEmpty()) talent = findWeaponTalent(doc, weaponName);
            add(result, seen, weaponName, talent);
        }
        return result;
    }

    /** Liest die Fernkampfwaffen aus dem tatsächlich aktiven Kampfset. */
    private static void mergeCalculatedWeapons(List<RangedWeapon> weapons, Document doc) {
        Element set = findActiveCombatSet(doc);
        if (set == null) return;

        NodeList containers = set.getElementsByTagName("fernkampfwaffen");
        Set<String> names = new HashSet<String>();
        for (RangedWeapon w : weapons) names.add(normalize(w.name));

        for (int i = 0; i < containers.getLength(); i++) {
            Element container = (Element) containers.item(i);
            if (container.hasAttribute("inbenutzung") && !"true".equalsIgnoreCase(container.getAttribute("inbenutzung"))) continue;

            NodeList list = container.getElementsByTagName("fernkampfwaffe");
            for (int j = 0; j < list.getLength(); j++) {
                Element e = (Element) list.item(j);
                String name = text(e, "name");
                if (name.isEmpty()) name = attr(e, "name");
                if (name.isEmpty()) continue;

                RangedWeapon w = find(weapons, name);
                if (w == null) {
                    w = new RangedWeapon();
                    w.name = name;
                    weapons.add(w);
                    names.add(normalize(name));
                }
                applyCalculated(w, e);
            }
        }
    }

    private static void add(List<RangedWeapon> list, Set<String> seen, String name, String talent) {
        if (name == null || name.trim().isEmpty()) return;
        RangedWeapon w = new RangedWeapon();
        w.name = name.trim();
        w.talent = talent == null ? "" : talent.trim();
        String key = normalize(w.name) + "|" + normalize(w.talent);
        if (seen.add(key)) list.add(w);
    }

    private static Element findActiveCombatSet(Document doc) {
        NodeList sets = doc.getElementsByTagName("kampfset");
        Element fallback = null;
        for (int i = 0; i < sets.getLength(); i++) {
            Element s = (Element) sets.item(i);
            if (fallback == null) fallback = s;
            if ("true".equalsIgnoreCase(attr(s, "inbenutzung"))
                    && "true".equalsIgnoreCase(attr(s, "tzm"))
                    && "1".equals(attr(s, "nr"))) return s;
        }
        for (int i = 0; i < sets.getLength(); i++) {
            Element s = (Element) sets.item(i);
            if ("true".equalsIgnoreCase(attr(s, "inbenutzung"))) return s;
        }
        return fallback;
    }

    private static void applyCalculated(RangedWeapon w, Element e) {
        String v;
        v = text(e, "at"); if (!v.isEmpty()) w.fk = v;
        v = text(e, "tp"); if (!v.isEmpty()) w.tp = v;
        v = text(e, "tpmod"); if (!v.isEmpty()) w.tpEntfernung = v;
        v = text(e, "kampftalent"); if (!v.isEmpty()) w.talent = v;
        v = text(e, "reichweite"); if (!v.isEmpty()) w.reichweite = v;
        v = text(e, "ladezeit"); if (!v.isEmpty()) w.lz = v;
        v = text(e, "tpkk"); if (!v.isEmpty()) w.tpkk = v;
        v = text(e, "ini"); if (!v.isEmpty()) w.ini = v;
        v = text(e, "wm"); if (!v.isEmpty()) w.mod = v;
        v = text(e, "munition"); if (!v.isEmpty()) w.munition = v;
        v = text(e, "minbf"); if (!v.isEmpty()) w.minbf = v;
        v = text(e, "aktbf"); if (!v.isEmpty()) w.aktbf = v;
    }

    private static RangedWeapon find(List<RangedWeapon> weapons, String name) {
        String n = normalize(name);
        for (RangedWeapon w : weapons) if (normalize(w.name).equals(n)) return w;
        return null;
    }

    private static Element findWeaponData(Document doc, String name) {
        if (name == null || name.isEmpty()) return null;
        NodeList items = doc.getElementsByTagName("gegenstand");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if (!name.equals(attr(item, "name"))) continue;
            NodeList fk = item.getElementsByTagName("Fernkampfwaffe");
            if (fk.getLength() > 0) return (Element) fk.item(0);
        }
        return null;
    }

    private static String findWeaponTalent(Document doc, String name) {
        Element e = findWeaponData(doc, name);
        return e == null ? "" : findNestedAttribute(e, "kampftalent");
    }

    private static String findNestedAttribute(Element root, String... keys) {
        String v = attr(root, keys); if (!v.isEmpty()) return v;
        NodeList c = root.getChildNodes();
        for (int i = 0; i < c.getLength(); i++) if (c.item(i) instanceof Element) {
            v = findNestedAttribute((Element) c.item(i), keys); if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        if (n.getLength() == 0) return "";
        Node x = n.item(0);
        return x.getTextContent() == null ? "" : x.getTextContent().trim();
    }

    private static String attr(Element e, String... keys) {
        for (String k : keys) {
            if (e.hasAttribute(k)) return e.getAttribute(k).trim();
            for (int i = 0; i < e.getAttributes().getLength(); i++) {
                Node n = e.getAttributes().item(i);
                if (n.getNodeName().equalsIgnoreCase(k)) return n.getNodeValue().trim();
            }
        }
        return "";
    }

    private static String normalize(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(); }
    private static String esc(String s) { if (s == null) return ""; return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#039;"); }

    private static String render(List<RangedWeapon> weapons) {
        StringBuilder b = new StringBuilder();
        b.append("<table class=\"fkwaffen modern-section\" id=\"section-fernkampfwaffen-10\"><tr><th class=\"titel\" colspan=\"13\">Fernkampfwaffen</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"fkwaffen gitternetz\"><thead><tr>");
        th(b, "name", "Fernkampfwaffe"); th(b, "typ", "Talent"); th(b, "tp", "TP"); th(b, "tpkk", "TP/KK"); th(b, "tp-entfernung", "TP/Entfernung"); th(b, "reichweite", "Reichweite"); th(b, "lz", "LZ"); th(b, "fk", "FK"); th(b, "ini", "INI"); th(b, "wm", "WM"); th(b, "munition", "Munition"); th(b, "minbf", "min BF"); th(b, "aktbf", "akt BF");
        b.append("</tr></thead><tbody>");
        for (RangedWeapon w : weapons) {
            b.append("<tr>"); td(b, "name", w.name); td(b, "typ", w.talent); td(b, "tp", w.tp); td(b, "tpkk", w.tpkk); td(b, "tp-entfernung", w.tpEntfernung); td(b, "reichweite", w.reichweite); td(b, "lz", w.lz); td(b, "fk", w.fk); td(b, "ini", w.ini); td(b, "wm", w.mod); td(b, "munition", w.munition); td(b, "minbf", w.minbf); td(b, "aktbf", w.aktbf); b.append("</tr>");
        }
        return b.append("</tbody></table></div></td></tr></table>").toString();
    }

    private static void th(StringBuilder b, String c, String v) { b.append("<th class=\"").append(c).append("\">").append(v).append("</th>"); }
    private static void td(StringBuilder b, String c, String v) { b.append("<td class=\"").append(c).append("\">").append(esc(v)).append("</td>"); }
    private static final class RangedWeapon { String name="", talent="", tp="", tpkk="", tpEntfernung="", reichweite="", lz="", fk="", ini="", mod="", munition="", minbf="", aktbf=""; }
}
