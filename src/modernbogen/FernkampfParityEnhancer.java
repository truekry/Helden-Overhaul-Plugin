package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Liest Fernkampfwaffen aus der tatsächlichen Helden-XML-Struktur. */
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
        NodeList equipped = doc.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < equipped.getLength(); i++) {
            Element e = (Element) equipped.item(i);
            String id = attr(e, "name");
            if (!id.toLowerCase().startsWith("fkwaffe")) continue;
            add(result, seen, create(e, attr(e, "waffenname"), attr(e, "talent"), doc));
        }
        NodeList items = doc.getElementsByTagName("gegenstand");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NodeList fk = item.getElementsByTagName("Fernkampfwaffe");
            if (fk.getLength() == 0) continue;
            Element fke = (Element) fk.item(0);
            String talent = "";
            NodeList ts = fke.getElementsByTagName("talente");
            if (ts.getLength() > 0) talent = attr((Element) ts.item(0), "kampftalent");
            add(result, seen, create(item, attr(item, "name"), talent, doc));
        }
        return result;
    }

    private static void add(List<RangedWeapon> list, Set<String> seen, RangedWeapon w) {
        if (w == null || w.name.isEmpty()) return;
        String key = normalize(w.name) + "|" + normalize(w.talent);
        if (seen.add(key)) list.add(w);
    }

    private static RangedWeapon create(Element source, String weaponName, String talent, Document doc) {
        RangedWeapon w = new RangedWeapon();
        w.name = weaponName;
        w.talent = talent;
        w.fk = findTalentValue(doc, talent);
        w.tp = first(source, "tp", "trefferpunkte", "schaden");
        w.tpkk = first(source, "tpkk", "tp/kk");
        w.tpEntfernung = first(source, "tpentfernung", "tpEntfernung", "tp-entfernung", "tp_entfernung", "tpentf", "schadenentfernung", "entfernungtp");
        w.reichweite = first(source, "reichweite", "rw", "entfernung");
        w.lz = first(source, "ladezeit", "lz", "reload");
        w.ini = first(source, "ini", "initiative");
        w.mod = first(source, "wm", "mod", "waffenmodifikator");
        w.munition = first(source, "munition", "munitionsart", "ammo");
        w.minbf = first(source, "minbf", "bfmin");
        w.aktbf = first(source, "aktbf", "bfakt", "bf");
        return w;
    }

    private static String findTalentValue(Document doc, String talentName) {
        if (doc == null || talentName == null || talentName.trim().isEmpty()) return "";
        String wanted = normalize(talentName);
        NodeList talents = doc.getElementsByTagName("talent");
        for (int i = 0; i < talents.getLength(); i++) {
            Element t = (Element) talents.item(i);
            if (wanted.equals(normalize(attr(t, "name")))) return attr(t, "value");
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
            td(b,"name",w.name); td(b,"typ",w.talent); td(b,"tp",w.tp); td(b,"tpkk",w.tpkk); td(b,"tp-entfernung",w.tpEntfernung);
            td(b,"reichweite",w.reichweite); td(b,"lz",w.lz); td(b,"fk",w.fk); td(b,"ini",w.ini); td(b,"wm",w.mod);
            td(b,"munition",w.munition); td(b,"minbf",w.minbf); td(b,"aktbf",w.aktbf); b.append("</tr>");
        }
        b.append("</tbody></table></div></td></tr></table>");
        return b.toString();
    }

    private static void th(StringBuilder b,String c,String v){b.append("<th class=\"").append(c).append("\">").append(v).append("</th>");}
    private static void td(StringBuilder b,String c,String v){b.append("<td class=\"").append(c).append("\">").append(esc(v)).append("</td>");}
    private static String first(Element e,String... keys){for(String k:keys){String v=attr(e,k);if(!v.isEmpty())return v;}return "";}
    private static String attr(Element e,String k){if(e.hasAttribute(k))return e.getAttribute(k).trim();for(int i=0;i<e.getAttributes().getLength();i++){Node n=e.getAttributes().item(i);if(n.getNodeName().equalsIgnoreCase(k))return n.getNodeValue().trim();}return "";}
    private static String normalize(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toLowerCase();}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#039;");}

    private static final class RangedWeapon {
        String name="", talent="", tp="", tpkk="", tpEntfernung="", reichweite="", lz="", fk="", ini="", mod="", munition="", minbf="", aktbf="";
    }
}
