package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Liest Fernkampfwaffen aus der Helden-XML. */
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
        List<RangedWeapon> result = new ArrayList<RangedWeapon>(); Set<String> seen = new HashSet<String>();
        NodeList equipped = doc.getElementsByTagName("heldenausruestung");
        for (int i=0;i<equipped.getLength();i++) {
            Element e=(Element)equipped.item(i); String id=attr(e,"name");
            if(!id.toLowerCase().startsWith("fkwaffe")) continue;
            String weaponName=attr(e,"waffenname"); String talent=attr(e,"talent");
            if(talent.isEmpty()) talent=findWeaponTalent(doc,weaponName);
            RangedWeapon w=create(doc,e,weaponName,talent);
            if(w!=null&&!w.name.isEmpty()&&seen.add(normalize(w.name)+"|"+normalize(w.talent))) result.add(w);
        }
        return result;
    }

    private static RangedWeapon create(Document doc,Element equipment,String weaponName,String talent){
        RangedWeapon w=new RangedWeapon(); w.name=weaponName; w.talent=talent;
        Element weaponData=findWeaponData(doc,weaponName);
        if(weaponData!=null){String t=findNestedAttribute(weaponData,"kampftalent");if(!t.isEmpty())w.talent=t;}
        w.taw=findTalentValue(doc,w.talent); w.fkBasis=findPropertyValue(doc,"fk");
        w.fk=Integer.toString(w.fkBasis+Math.floorDiv(w.taw,2));
        if(weaponData!=null){
            w.tp=findNestedAttribute(weaponData,"tp","trefferpunkte","schaden");
            w.tpkk=findNestedAttribute(weaponData,"tpkk","tp/kk");
            w.tpEntfernung=findNestedAttribute(weaponData,"tpentfernung","tpEntfernung","tp-entfernung","tp_entfernung","tpentf","schadenentfernung","entfernungtp");
            w.reichweite=findNestedAttribute(weaponData,"reichweite","rw","entfernung");
            w.lz=findNestedAttribute(weaponData,"ladezeit","lz","reload");
            w.ini=findNestedAttribute(weaponData,"ini","initiative"); w.mod=findNestedAttribute(weaponData,"wm","mod","waffenmodifikator");
            w.munition=findNestedAttribute(weaponData,"munition","munitionsart","ammo"); w.minbf=findNestedAttribute(weaponData,"minbf","bfmin"); w.aktbf=findNestedAttribute(weaponData,"aktbf","bfakt","bf");
        }
        if(w.minbf.isEmpty())w.minbf=attr(equipment,"bfmin"); if(w.aktbf.isEmpty())w.aktbf=attr(equipment,"bfakt"); return w;
    }
    private static Element findWeaponData(Document doc,String weaponName){if(weaponName==null||weaponName.isEmpty())return null;NodeList items=doc.getElementsByTagName("gegenstand");for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);if(!weaponName.equals(attr(item,"name")))continue;NodeList fk=item.getElementsByTagName("Fernkampfwaffe");if(fk.getLength()>0)return(Element)fk.item(0);}return null;}
    private static String findWeaponTalent(Document doc,String weaponName){Element e=findWeaponData(doc,weaponName);return e==null?"":findNestedAttribute(e,"kampftalent");}
    private static int findTalentValue(Document doc,String name){if(name==null||name.trim().isEmpty())return 0;String wanted=normalize(name);NodeList n=doc.getElementsByTagName("talent");for(int i=0;i<n.getLength();i++){Element e=(Element)n.item(i);if(wanted.equals(normalize(attr(e,"name"))))return parseInt(attr(e,"value"),0);}return 0;}
    private static int findPropertyValue(Document doc,String name){NodeList n=doc.getElementsByTagName("eigenschaft");for(int i=0;i<n.getLength();i++){Element e=(Element)n.item(i);if(name.equalsIgnoreCase(attr(e,"name")))return parseInt(attr(e,"value"),0);}return 0;}
    private static String findNestedAttribute(Element root,String...keys){String v=attr(root,keys);if(!v.isEmpty())return v;NodeList c=root.getChildNodes();for(int i=0;i<c.getLength();i++)if(c.item(i)instanceof Element){v=findNestedAttribute((Element)c.item(i),keys);if(!v.isEmpty())return v;}return "";}
    private static String render(List<RangedWeapon> weapons){StringBuilder b=new StringBuilder();b.append("<table class=\"fkwaffen modern-section\" id=\"section-fernkampfwaffen-10\"><tr><th class=\"titel\" colspan=\"13\">Fernkampfwaffen</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"fkwaffen gitternetz\"><thead><tr>");th(b,"name","Fernkampfwaffe");th(b,"typ","Talent");th(b,"tp","TP");th(b,"tpkk","TP/KK");th(b,"tp-entfernung","TP/Entfernung");th(b,"reichweite","Reichweite");th(b,"lz","LZ");th(b,"fk","FK");th(b,"ini","INI");th(b,"wm","WM");th(b,"munition","Munition");th(b,"minbf","min BF");th(b,"aktbf","akt BF");b.append("</tr></thead><tbody>");for(RangedWeapon w:weapons){b.append("<tr>");td(b,"name",w.name);td(b,"typ",w.talent);td(b,"tp",w.tp);td(b,"tpkk",w.tpkk);td(b,"tp-entfernung",w.tpEntfernung);td(b,"reichweite",w.reichweite);td(b,"lz",w.lz);td(b,"fk",w.fk);td(b,"ini",w.ini);td(b,"wm",w.mod);td(b,"munition",w.munition);td(b,"minbf",w.minbf);td(b,"aktbf",w.aktbf);b.append("</tr>");}return b.append("</tbody></table></div></td></tr></table>").toString();}
    private static void th(StringBuilder b,String c,String v){b.append("<th class=\"").append(c).append("\">").append(v).append("</th>");}
    private static void td(StringBuilder b,String c,String v){b.append("<td class=\"").append(c).append("\">").append(esc(v)).append("</td>");}
    private static String attr(Element e,String...keys){for(String k:keys){if(e.hasAttribute(k))return e.getAttribute(k).trim();for(int i=0;i<e.getAttributes().getLength();i++){Node n=e.getAttributes().item(i);if(n.getNodeName().equalsIgnoreCase(k))return n.getNodeValue().trim();}}return "";}
    private static int parseInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}
    private static String normalize(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toLowerCase();}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#039;");}
    private static final class RangedWeapon{String name="",talent="",tp="",tpkk="",tpEntfernung="",reichweite="",lz="",fk="",ini="",mod="",munition="",minbf="",aktbf="";int taw=0,fkBasis=0;}
}
