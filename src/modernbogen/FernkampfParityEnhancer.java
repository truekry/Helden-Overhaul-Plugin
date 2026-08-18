package modernbogen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Fernkampf-Parität: XML für Zuordnung/FK, native HTML für Waffenwerte. */
public final class FernkampfParityEnhancer {
    private FernkampfParityEnhancer() {}

    public static String enhance(String html, Document doc) { return enhance(html, doc, null); }

    public static String enhance(String html, Document doc, String nativeHtml) {
        if (html == null) return html;
        if (html.contains("id=\"section-fernkampfwaffen-10\"") || html.contains("class=\"fkwaffen modern-section\"")) return html;

        List<RangedWeapon> weapons = doc == null ? new ArrayList<RangedWeapon>() : read(doc);
        if (nativeHtml != null && !nativeHtml.trim().isEmpty()) {
            Map<String, NativeRow> rows = parseNativeRows(nativeHtml);
            mergeNative(weapons, rows);
            // Falls die XML-Abfrage der Plugin-API keine Heldendaten geliefert
            // hat, darf die native HTML-Ausgabe den Bereich trotzdem erzeugen.
            if (weapons.isEmpty()) {
                for (NativeRow r : rows.values()) {
                    if (!r.name.isEmpty()) {
                        RangedWeapon w = new RangedWeapon();
                        w.name = r.name;
                        copy(w, r);
                        weapons.add(w);
                    }
                }
            }
        }
        if (weapons.isEmpty()) return html;

        String section = render(weapons);
        int marker = findInsertionPoint(html);
        if (marker < 0) {
            // Als letzter Fallback vor </body>. So verschwindet der FK-Bereich
            // nicht nur deshalb, weil sich eine andere Sektion geändert hat.
            int body = html.toLowerCase().lastIndexOf("</body>");
            marker = body >= 0 ? body : html.length();
        }
        return html.substring(0, marker) + section + "\n" + html.substring(marker);
    }

    private static int findInsertionPoint(String html) {
        String[] markers = {
            "<table class=\"ruestungen modern-section\"",
            "<table class=\"schilder modern-section\"",
            "<table class=\"inventar modern-section\"",
            "id=\"section-ruestungen-11\"",
            "id=\"section-schilder-12\"",
            "id=\"section-inventar-10\""
        };
        for (String marker : markers) {
            int p = html.indexOf(marker);
            if (p >= 0) {
                int table = html.lastIndexOf("<table", p);
                return table >= 0 ? table : p;
            }
        }
        return -1;
    }

    private static List<RangedWeapon> read(Document doc) {
        List<RangedWeapon> result = new ArrayList<RangedWeapon>();
        Set<String> seen = new HashSet<String>();
        NodeList equipped = doc.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < equipped.getLength(); i++) {
            Element e = (Element) equipped.item(i);
            String id = attr(e, "name");
            if (!id.toLowerCase().startsWith("fkwaffe")) continue;
            String weaponName = attr(e, "waffenname");
            String talent = attr(e, "talent");
            if (talent.isEmpty()) talent = findWeaponTalent(doc, weaponName);
            RangedWeapon w = create(doc, e, weaponName, talent);
            if (w != null && !w.name.isEmpty() && seen.add(normalize(w.name) + "|" + normalize(w.talent))) result.add(w);
        }
        return result;
    }

    private static RangedWeapon create(Document doc, Element equipment, String weaponName, String talent) {
        RangedWeapon w = new RangedWeapon();
        w.name = weaponName; w.talent = talent;
        Element weaponData = findWeaponData(doc, weaponName);
        if (weaponData != null) {
            String t = findNestedAttribute(weaponData, "kampftalent");
            if (!t.isEmpty()) w.talent = t;
        }
        w.taw = findTalentValue(doc, w.talent);
        w.fkBasis = findPropertyValue(doc, "fk");
        // FK = AT des zugeordneten Fernkampftalents. Die FK-Basis wird mit
        // dem halben TaW (abgerundet) ergänzt.
        w.fk = Integer.toString(w.fkBasis + Math.floorDiv(w.taw, 2));
        if (weaponData != null) {
            w.tp = findNestedAttribute(weaponData, "tp", "trefferpunkte", "schaden");
            w.tpkk = findNestedAttribute(weaponData, "tpkk", "tp/kk");
            w.tpEntfernung = findNestedAttribute(weaponData, "tpentfernung", "tpEntfernung", "tp-entfernung", "tp_entfernung", "tpentf", "schadenentfernung", "entfernungtp");
            w.reichweite = findNestedAttribute(weaponData, "reichweite", "rw", "entfernung");
            w.lz = findNestedAttribute(weaponData, "ladezeit", "lz", "reload");
            w.ini = findNestedAttribute(weaponData, "ini", "initiative");
            w.mod = findNestedAttribute(weaponData, "wm", "mod", "waffenmodifikator");
            w.munition = findNestedAttribute(weaponData, "munition", "munitionsart", "ammo");
            w.minbf = findNestedAttribute(weaponData, "minbf", "bfmin");
            w.aktbf = findNestedAttribute(weaponData, "aktbf", "bfakt", "bf");
        }
        if (w.minbf.isEmpty()) w.minbf = attr(equipment, "bfmin");
        if (w.aktbf.isEmpty()) w.aktbf = attr(equipment, "bfakt");
        return w;
    }

    private static void mergeNative(List<RangedWeapon> weapons, Map<String, NativeRow> rows) {
        for (RangedWeapon w : weapons) {
            NativeRow r = rows.get(normalize(w.name));
            if (r != null) copy(w, r);
        }
    }

    private static void copy(RangedWeapon w, NativeRow r) {
        if (!r.tp.isEmpty()) w.tp = r.tp;
        if (!r.tpkk.isEmpty()) w.tpkk = r.tpkk;
        if (!r.tpEntfernung.isEmpty()) w.tpEntfernung = r.tpEntfernung;
        if (!r.reichweite.isEmpty()) w.reichweite = r.reichweite;
        if (!r.lz.isEmpty()) w.lz = r.lz;
        if (!r.ini.isEmpty()) w.ini = r.ini;
        if (!r.mod.isEmpty()) w.mod = r.mod;
        if (!r.munition.isEmpty()) w.munition = r.munition;
        if (!r.minbf.isEmpty()) w.minbf = r.minbf;
        if (!r.aktbf.isEmpty()) w.aktbf = r.aktbf;
    }

    private static Map<String, NativeRow> parseNativeRows(String html) {
        Map<String, NativeRow> result = new HashMap<String, NativeRow>();
        Matcher tm = Pattern.compile("(?is)<table\\b[^>]*>.*?</table>").matcher(html);
        while (tm.find()) {
            String table = tm.group();
            String low = clean(table).toLowerCase();
            if (!low.contains("fernkampf") && !low.contains("reichweite") && !low.contains("tp/entfernung")) continue;
            List<String> headers = cells(table, "th");
            if (headers.isEmpty()) headers = cells(table, "td");
            if (headers.isEmpty()) continue;
            Matcher rm = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(table);
            boolean first = true;
            while (rm.find()) {
                List<String> cs = cells(rm.group(1), "td");
                if (cs.size() < 2) continue;
                if (first && cells(rm.group(1), "th").size() > 0) { first = false; continue; }
                NativeRow row = new NativeRow();
                for (int i = 0; i < headers.size() && i < cs.size(); i++) set(row, headers.get(i), cs.get(i));
                if (!row.name.isEmpty()) result.put(normalize(row.name), row);
            }
        }
        return result;
    }

    private static List<String> cells(String html, String tag) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("(?is)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">").matcher(html);
        while (m.find()) out.add(clean(m.group(1)));
        return out;
    }

    private static void set(NativeRow r, String header, String value) {
        String h = normalize(header).replace(" ", "");
        if (h.contains("fernkampfwaffe") || h.equals("waffe") || h.equals("name")) r.name = value;
        else if (h.equals("tp")) r.tp = value;
        else if (h.contains("tp/kk") || h.equals("tpkk")) r.tpkk = value;
        else if (h.contains("tp/entfernung") || h.contains("tpentfernung")) r.tpEntfernung = value;
        else if (h.contains("reichweite") || h.equals("rw")) r.reichweite = value;
        else if (h.equals("lz") || h.contains("ladezeit")) r.lz = value;
        else if (h.equals("ini") || h.contains("initiative")) r.ini = value;
        else if (h.equals("wm") || h.contains("waffenmod")) r.mod = value;
        else if (h.contains("munition")) r.munition = value;
        else if (h.contains("minbf")) r.minbf = value;
        else if (h.contains("aktbf")) r.aktbf = value;
    }

    private static String clean(String s) {
        return s.replaceAll("(?is)<script.*?</script>|<style.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").trim();
    }

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
    private static String findWeaponTalent(Document doc, String weaponName) { Element e = findWeaponData(doc, weaponName); return e == null ? "" : findNestedAttribute(e, "kampftalent"); }
    private static int findTalentValue(Document doc, String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        NodeList n = doc.getElementsByTagName("talent"); String wanted = normalize(name);
        for (int i=0;i<n.getLength();i++) { Element e=(Element)n.item(i); if(wanted.equals(normalize(attr(e,"name")))) return parseInt(attr(e,"value"),0); }
        return 0;
    }
    private static int findPropertyValue(Document doc, String name) {
        NodeList n=doc.getElementsByTagName("eigenschaft");
        for(int i=0;i<n.getLength();i++){Element e=(Element)n.item(i);if(name.equalsIgnoreCase(attr(e,"name")))return parseInt(attr(e,"value"),0);}return 0;
    }
    private static String findNestedAttribute(Element root, String... keys) {
        String v=attr(root,keys); if(!v.isEmpty())return v; NodeList c=root.getChildNodes();
        for(int i=0;i<c.getLength();i++)if(c.item(i) instanceof Element){v=findNestedAttribute((Element)c.item(i),keys);if(!v.isEmpty())return v;}return "";
    }
    private static String attr(Element e,String...keys){for(String k:keys){if(e.hasAttribute(k))return e.getAttribute(k).trim();for(int i=0;i<e.getAttributes().getLength();i++){Node n=e.getAttributes().item(i);if(n.getNodeName().equalsIgnoreCase(k))return n.getNodeValue().trim();}}return "";}
    private static int parseInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}
    private static String normalize(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toLowerCase();}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#039;");}

    private static String render(List<RangedWeapon> weapons){StringBuilder b=new StringBuilder();
        b.append("<table class=\"fkwaffen modern-section\" id=\"section-fernkampfwaffen-10\"><tr><th class=\"titel\" colspan=\"13\">Fernkampfwaffen</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"fkwaffen gitternetz\"><thead><tr>");
        th(b,"name","Fernkampfwaffe");th(b,"typ","Talent");th(b,"tp","TP");th(b,"tpkk","TP/KK");th(b,"tp-entfernung","TP/Entfernung");th(b,"reichweite","Reichweite");th(b,"lz","LZ");th(b,"fk","FK");th(b,"ini","INI");th(b,"wm","WM");th(b,"munition","Munition");th(b,"minbf","min BF");th(b,"aktbf","akt BF");b.append("</tr></thead><tbody>");
        for(RangedWeapon w:weapons){b.append("<tr>");td(b,"name",w.name);td(b,"typ",w.talent);td(b,"tp",w.tp);td(b,"tpkk",w.tpkk);td(b,"tp-entfernung",w.tpEntfernung);td(b,"reichweite",w.reichweite);td(b,"lz",w.lz);td(b,"fk",w.fk);td(b,"ini",w.ini);td(b,"wm",w.mod);td(b,"munition",w.munition);td(b,"minbf",w.minbf);td(b,"aktbf",w.aktbf);b.append("</tr>");}
        return b.append("</tbody></table></div></td></tr></table>").toString();}
    private static void th(StringBuilder b,String c,String v){b.append("<th class=\"").append(c).append("\">").append(v).append("</th>");}
    private static void td(StringBuilder b,String c,String v){b.append("<td class=\"").append(c).append("\">").append(esc(v)).append("</td>");}
    private static final class NativeRow{String name="",tp="",tpkk="",tpEntfernung="",reichweite="",lz="",ini="",mod="",munition="",minbf="",aktbf="";}
    private static final class RangedWeapon{String name="",talent="",tp="",tpkk="",tpEntfernung="",reichweite="",lz="",fk="",ini="",mod="",munition="",minbf="",aktbf="";int taw=0,fkBasis=0;}
}
