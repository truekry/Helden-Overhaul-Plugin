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
 * Liest Fernkampfwaffen aus der Helden-XML.
 *
 * Die XML enthält die Zuordnung Waffe -> Fernkampftalent in
 * gegenstand/Fernkampfwaffe/talente@kampftalent. Der FK-Angriffswert ist
 * kein Attribut der Waffe: Er wird aus der FK-Basis und dem TaW des
 * zugeordneten Fernkampftalents bestimmt.
 */
public final class FernkampfParityEnhancer {
    private FernkampfParityEnhancer() {}

    public static String enhance(String html, Document doc) {
        if (html == null || doc == null) return html;
        List<RangedWeapon> weapons = read(doc);
        if (weapons.isEmpty() || html.contains("<table class=\"fkwaffen modern-section\"")) return html;

        String section = render(weapons);
        int marker = html.indexOf("<table class=\"ruestungen modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"schilde modern-section\"");
        if (marker < 0) marker = html.indexOf("<table class=\"inventar modern-section\"");
        if (marker < 0) return html;
        return html.substring(0, marker) + section + "\n" + html.substring(marker);
    }

    private static List<RangedWeapon> read(Document doc) {
        List<RangedWeapon> result = new ArrayList<RangedWeapon>();
        Set<String> seen = new HashSet<String>();

        // Entscheidend sind die ausgerüsteten fkwaffe*-Einträge.
        // So werden auch Waffen mit ungewöhnlichen Namen und Waffen, die
        // sowohl Nah- als auch Fernkampf erlauben, korrekt behandelt.
        NodeList equipped = doc.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < equipped.getLength(); i++) {
            Element e = (Element) equipped.item(i);
            String id = attr(e, "name");
            if (!id.toLowerCase().startsWith("fkwaffe")) continue;

            String weaponName = attr(e, "waffenname");
            String talent = attr(e, "talent");

            // Falls der Ausrüstungsdatensatz kein Talent enthält, verwenden
            // wir die semantische Fernkampfwaffen-Definition des Gegenstands.
            if (talent.isEmpty()) talent = findWeaponTalent(doc, weaponName);

            add(result, seen, create(doc, e, weaponName, talent));
        }
        return result;
    }

    private static void add(List<RangedWeapon> list, Set<String> seen, RangedWeapon w) {
        if (w == null || w.name.isEmpty()) return;
        String key = normalize(w.name) + "|" + normalize(w.talent);
        if (seen.add(key)) list.add(w);
    }

    private static RangedWeapon create(Document doc, Element equipment, String weaponName, String talent) {
        RangedWeapon w = new RangedWeapon();
        w.name = weaponName;
        w.talent = talent;

        // Die Beispiel-XML hat genau diese Zuordnung:
        // Kurzbogen -> Fernkampfwaffe -> kampftalent="Bogen".
        Element weaponData = findWeaponData(doc, weaponName);
        if (weaponData != null) {
            String dataTalent = findNestedAttribute(weaponData, "kampftalent");
            if (!dataTalent.isEmpty()) w.talent = dataTalent;
        }

        w.taw = findTalentValue(doc, w.talent);
        w.fkBasis = findPropertyValue(doc, "fk");
        w.fk = calculateFk(w.fkBasis, w.taw);

        // Diese Werte werden übernommen, falls eine Helden-Software-Version
        // sie im Fernkampfwaffen-Datensatz mitliefert. In der mitgelieferten
        // Beispielheld-XML sind sie nicht vorhanden.
        if (weaponData != null) {
            w.tp = findNestedAttribute(weaponData, "tp", "trefferpunkte", "schaden");
            w.tpkk = findNestedAttribute(weaponData, "tpkk", "tp/kk");
            w.tpEntfernung = findNestedAttribute(weaponData,
                    "tpentfernung", "tpEntfernung", "tp-entfernung", "tp_entfernung",
                    "tpentf", "schadenentfernung", "entfernungtp");
            w.reichweite = findNestedAttribute(weaponData, "reichweite", "rw", "entfernung");
            w.lz = findNestedAttribute(weaponData, "ladezeit", "lz", "reload");
            w.ini = findNestedAttribute(weaponData, "ini", "initiative");
            w.mod = findNestedAttribute(weaponData, "wm", "mod", "waffenmodifikator");
            w.munition = findNestedAttribute(weaponData, "munition", "munitionsart", "ammo");
            w.minbf = findNestedAttribute(weaponData, "minbf", "bfmin");
            w.aktbf = findNestedAttribute(weaponData, "aktbf", "bfakt", "bf");
        }

        // Ausrüstung selbst kann ebenfalls aktuelle Waffenwerte enthalten.
        if (w.minbf.isEmpty()) w.minbf = attr(equipment, "bfmin");
        if (w.aktbf.isEmpty()) w.aktbf = attr(equipment, "bfakt");
        return w;
    }

    /** Findet die Fernkampfdefinition des Gegenstands anhand seines Namens. */
    private static Element findWeaponData(Document doc, String weaponName) {
        if (weaponName == null || weaponName.isEmpty()) return null;
        NodeList items = doc.getElementsByTagName("gegenstand");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if (!weaponName.equals(attr(item, "name"))) continue;
            NodeList fk = item.getElementsByTagName("Fernkampfwaffe");
            if (fk.getLength() > 0) return (Element) fk.item(0);
        }
        return null;
    }

    private static String findWeaponTalent(Document doc, String weaponName) {
        Element data = findWeaponData(doc, weaponName);
        return data == null ? "" : findNestedAttribute(data, "kampftalent");
    }

    /**
     * Ermittelt den Talentwert. In der XML ist dieser als talent@value
     * gespeichert, z.B. Bogen value="7".
     */
    private static int findTalentValue(Document doc, String talentName) {
        if (doc == null || talentName == null || talentName.trim().isEmpty()) return 0;
        String wanted = normalize(talentName);
        NodeList talents = doc.getElementsByTagName("talent");
        for (int i = 0; i < talents.getLength(); i++) {
            Element t = (Element) talents.item(i);
            if (wanted.equals(normalize(attr(t, "name")))) return parseInt(attr(t, "value"), 0);
        }
        return 0;
    }

    /**
     * Fernkampf hat keine Parade. FK ist daher der Angriffswert:
     * FK-Basis + halber TaW, abgerundet.
     * Beispielheld: FK-Basis 8 + TaW Bogen 7 / 2 = FK 11.
     */
    private static String calculateFk(int fkBasis, int taw) {
        return Integer.toString(fkBasis + Math.floorDiv(taw, 2));
    }

    private static int findPropertyValue(Document doc, String propertyName) {
        NodeList properties = doc.getElementsByTagName("eigenschaft");
        for (int i = 0; i < properties.getLength(); i++) {
            Element e = (Element) properties.item(i);
            if (propertyName.equalsIgnoreCase(attr(e, "name"))) return parseInt(attr(e, "value"), 0);
        }
        return 0;
    }

    private static String findNestedAttribute(Element root, String... keys) {
        String value = attr(root, keys);
        if (!value.isEmpty()) return value;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            value = findNestedAttribute((Element) n, keys);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String render(List<RangedWeapon> weapons) {
        StringBuilder b = new StringBuilder();
        b.append("<table class=\"fkwaffen modern-section\" id=\"section-fernkampfwaffen-10\">");
        b.append("<tr><th class=\"titel\" colspan=\"13\">Fernkampfwaffen</th></tr>");
        b.append("<tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        b.append("<table class=\"fkwaffen gitternetz\"><thead><tr>");
        th(b,"name","Fernkampfwaffe"); th(b,"typ","Talent"); th(b,"tp","TP"); th(b,"tpkk","TP/KK");
        th(b,"tp-entfernung","TP/Entfernung"); th(b,"reichweite","Reichweite"); th(b,"lz","LZ"); th(b,"fk","FK");
        th(b,"ini","INI"); th(b,"wm","WM"); th(b,"munition","Munition"); th(b,"minbf","min BF"); th(b,"aktbf","akt BF");
        b.append("</tr></thead><tbody>");
        for (RangedWeapon w : weapons) {
            b.append("<tr>");
            td(b,"name",w.name); td(b,"typ",w.talent); td(b,"tp",w.tp); td(b,"tpkk",w.tpkk);
            td(b,"tp-entfernung",w.tpEntfernung); td(b,"reichweite",w.reichweite); td(b,"lz",w.lz);
            td(b,"fk",w.fk); td(b,"ini",w.ini); td(b,"wm",w.mod); td(b,"munition",w.munition);
            td(b,"minbf",w.minbf); td(b,"aktbf",w.aktbf); b.append("</tr>");
        }
        b.append("</tbody></table></div></td></tr></table>");
        return b.toString();
    }

    private static void th(StringBuilder b, String c, String v) { b.append("<th class=\"").append(c).append("\">").append(v).append("</th>"); }
    private static void td(StringBuilder b, String c, String v) { b.append("<td class=\"").append(c).append("\">").append(esc(v)).append("</td>"); }

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

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private static String normalize(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(); }
    private static String esc(String s) { if (s == null) return ""; return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#039;"); }

    private static final class RangedWeapon {
        String name="", talent="", tp="", tpkk="", tpEntfernung="", reichweite="", lz="", fk="", ini="", mod="", munition="", minbf="", aktbf="";
        int taw=0, fkBasis=0;
    }
}
