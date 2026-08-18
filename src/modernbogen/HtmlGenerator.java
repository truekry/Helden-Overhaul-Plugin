package modernbogen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class HtmlGenerator {
    private HtmlGenerator() {}

    public static String extractHeldName(Document doc) {
        Element daten = findRoot(doc, "daten");
        if (daten != null) {
            String n = text(child(daten, "angaben"), "name");
            if (!n.isEmpty()) return n;
        }
        Element held = firstHeld(doc);
        if (held != null && held.hasAttribute("name")) {
            return held.getAttribute("name");
        }
        return "Held";
    }

    public static String generate(Document doc) {
        Element daten = findRoot(doc, "daten");
        if (daten != null) {
            return generateFromDaten(daten);
        }
        Element held = firstHeld(doc);
        if (held != null) {
            return generateFromHeld(held);
        }
        String root = doc.getDocumentElement() != null ? doc.getDocumentElement().getTagName() : "?";
        return "<!doctype html><html><body><p>Kein Held in XML (Root: " + esc(root) + ").</p></body></html>";
    }

    private static Element firstHeld(Document doc) {
        NodeList list = doc.getElementsByTagNameNS("*", "held");
        if (list.getLength() == 0) list = doc.getElementsByTagName("held");
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String generateFromDaten(Element daten) {
        Element angaben = child(daten, "angaben");
        String name = text(angaben, "name");
        if (name.isEmpty()) name = "Held";

        Map<String, String[]> props = readEig(daten);
        List<String[]> rkp = readRKP(angaben);
        List<String[]> person = readBeschreibung(angaben);
        List<String[]> ap = readAp(angaben);
        List<String> vorteile = new ArrayList<String>();
        List<String> nachteile = new ArrayList<String>();
        readVorteileNachteile(daten, vorteile, nachteile);
        List<String> sfs = readSf(daten);
        Map<String, List<Talent>> talentGruppen = readTalenteGruppiert(daten);
        List<Zauber> zauber = readZauber(daten);
        if (zauber.isEmpty()) {
            // Manche Exporte nutzen zauberliste statt zauber
            zauber = readZauberHeld(child(daten, "zauberliste"));
            if (zauber.isEmpty()) {
                NodeList zl = daten.getElementsByTagName("zauberliste");
                if (zl.getLength() > 0) zauber = readZauberHeld((Element) zl.item(0));
            }
        }
        String zauberDebug = "";
        if (zauber.isEmpty()) {
            StringBuilder db = new StringBuilder();
            NodeList ch = daten.getChildNodes();
            for (int i = 0; i < ch.getLength(); i++) {
                if (ch.item(i) instanceof Element) {
                    if (db.length() > 0) db.append(",");
                    db.append(localName(ch.item(i)));
                }
            }
            zauberDebug = db.toString();
        }
        List<Waffe> nkw = readNahkampf(daten);
        List<Ruestung> ruestungen = readRuestungen(daten);
        List<Schild> schilder = readSchilder(daten);
        List<Item> items = readItems(daten);
        String portrait = firstNonEmpty(text(angaben, "bildPfad"), text(angaben, "bild"), attr(angaben, "bild", ""));

        // Elfen-Repräsentation: pro Zauber KL→IN wenn REP=Elf und IN>KL
        applyElfRep(zauber, props);

        List<String[]> personRechts = readBeschreibungRechts(angaben, props);
        return renderSheet(name, props, rkp, person, personRechts, ap, vorteile, nachteile, sfs,
                talentGruppen, zauber, nkw, ruestungen, schilder, items, portrait, zauberDebug);
    }

    private static String renderSheet(String name, Map<String, String[]> props,
            List<String[]> rkp, List<String[]> person, List<String[]> personRechts,
            List<String[]> ap, List<String> vorteile, List<String> nachteile, List<String> sfs,
            Map<String, List<Talent>> talentGruppen, List<Zauber> zauber, List<Waffe> nkw,
            List<Ruestung> ruestungen, List<Schild> schilder,
            List<Item> items, String portrait, String zauberDebug) {
        StringBuilder sb = new StringBuilder(150000);
        sb.append("<!doctype html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"de\">\n<head>\n");
        sb.append("<meta content=\"text/html; charset=UTF-8\" http-equiv=\"content-type\"/>\n");
        sb.append("<title>").append(esc(name)).append("</title>\n");
        sb.append("<style type=\"text/css\" id=\"modern-character-sheet-style\">\n");
        sb.append(EmbeddedCss.CSS);
        sb.append("\n/* Layout-Hilfen wie Vorlage */\n");
        sb.append(".talente > tbody > tr > td.links, .talente > tbody > tr > td.rechts { width:50%; vertical-align:top; }\n");
        sb.append(".vorteile td.name { vertical-align:top; width:50%; }\n");
        sb.append(".inventar.gitternetz td { width:auto; }\n");
        sb.append("img.heldenportraet { display:block; width:min(180px,100%); aspect-ratio:3/4; object-fit:cover; object-position:center top; max-height:240px; margin:0 auto; }\n");
        sb.append(".eigenschaften-split { width:100%; border-collapse:collapse; }\n");
        sb.append(".eigenschaften-split > tbody > tr > td { width:50%; vertical-align:top; padding:0 8px; }\n");
        sb.append(".ap-horizontal { width:100%; }\n");
        sb.append(".ap-horizontal td { text-align:center; padding:8px 12px; }\n");
        sb.append(".ap-horizontal .name { display:block; font-size:0.85rem; color:var(--muted); font-weight:650; }\n");
        sb.append(".ap-horizontal .eintrag { display:block; font-size:1.2rem; font-weight:800; color:var(--accent); }\n");
        sb.append(".sf-zwei-spalten { width:100%; }\n");
        sb.append(".sf-zwei-spalten td { width:50%; vertical-align:top; }\n");
        sb.append("\n</style>\n</head>\n<body>\n");

        sb.append("<h1 class=\"heldenname\">").append(esc(name)).append("</h1>\n");
        boolean hasVn = !vorteile.isEmpty() || !nachteile.isEmpty();
        boolean hasSf = !sfs.isEmpty();
        boolean hasTalente = talentGruppen != null && !talentGruppen.isEmpty();
        boolean hasZauber = zauber != null && !zauber.isEmpty();
        boolean hasNkw = nkw != null && !nkw.isEmpty();
        boolean hasRuestung = ruestungen != null && !ruestungen.isEmpty();
        boolean hasSchild = schilder != null && !schilder.isEmpty();
        boolean hasItems = items != null && !items.isEmpty();
        boolean hasAp = ap != null && !ap.isEmpty();

        sb.append("<nav class=\"modern-nav\" aria-label=\"Charakterbogen Navigation\">");
        sb.append("<button type=\"button\" id=\"theme-toggle\" aria-label=\"Dark Mode aktivieren\" title=\"Dark Mode\" aria-pressed=\"false\">☾</button>");
        sb.append("<a href=\"#section-personendaten-1\">Personendaten</a>");
        sb.append("<a href=\"#section-beschreibung-2\">Beschreibung</a>");
        sb.append("<a href=\"#section-eigenschaften-und-basiswerte-3\">Basiswerte</a>");
        if (hasAp) sb.append("<a href=\"#section-abenteuerpunkte-4\">Abenteuerpunkte</a>");
        if (hasVn) sb.append("<a href=\"#section-vorteile-und-nachteile-5\">Vorteile und Nachteile</a>");
        if (hasSf) sb.append("<a href=\"#section-sonderfertigkeiten-6\">Sonderfertigkeiten</a>");
        if (hasTalente) sb.append("<a href=\"#section-talente-7\">Talente</a>");
        if (hasZauber) sb.append("<a href=\"#section-zauber-8\">Zauber</a>");
        if (hasNkw) sb.append("<a href=\"#section-nahkampfwaffen-9\">Nahkampfwaffen</a>");
        if (hasRuestung) sb.append("<a href=\"#section-ruestungen-11\">Rüstungen</a>");
        if (hasSchild) sb.append("<a href=\"#section-schilder-12\">Schilde</a>");
        if (hasItems) sb.append("<a href=\"#section-inventar-10\">Inventar</a>");
        sb.append("<button type=\"button\" class=\"nav-dice\" data-sides=\"6\" title=\"1W6 würfeln\" aria-label=\"1W6 würfeln\">🎲6</button>");
        sb.append("<button type=\"button\" class=\"nav-dice\" data-sides=\"20\" title=\"1W20 würfeln\" aria-label=\"1W20 würfeln\">🎲20</button>");
        sb.append("</nav>\n");

        // Personendaten
        sb.append("<table class=\"personendaten modern-section\" id=\"section-personendaten-1\">");
        sb.append("<tr><th class=\"titel\" colspan=\"2\">Personendaten</th></tr><tr>");
        sb.append("<td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"heldendaten\">");
        for (String[] row : rkp) {
            sb.append("<tr><td class=\"name\">").append(esc(row[0])).append("</td><td class=\"eintrag\">").append(esc(row[1])).append("</td></tr>");
        }
        sb.append("</table></div></td></tr></table>\n");

        // Beschreibung: links Person, mitte Portrait, rechts Stand/Titel/So/Familie
        sb.append("<table class=\"beschreibung modern-section\" id=\"section-beschreibung-2\">");
        sb.append("<tr><th class=\"titel\" colspan=\"3\">Beschreibung</th></tr><tr>");
        sb.append("<td class=\"links\"><div class=\"links_innen\"><table class=\"persoenliches\">");
        for (String[] row : person) {
            sb.append("<tr><td class=\"name\">").append(esc(row[0])).append("</td><td class=\"eintrag\">").append(esc(row[1])).append("</td></tr>");
        }
        sb.append("</table></div></td>");
        sb.append("<td class=\"mitte\"><div class=\"mitte_innen\">");
        if (portrait != null && !portrait.isEmpty()) {
            String src = portrait.startsWith("data:") || portrait.startsWith("http") || portrait.startsWith("file:")
                    ? portrait : ("file:///" + portrait.replace("\\", "/"));
            sb.append("<img class=\"heldenportraet\" alt=\"Porträt von ").append(esc(name)).append("\" src=\"").append(esc(src)).append("\"/>");
        }
        sb.append("</div></td>");
        sb.append("<td class=\"rechts\"><div class=\"rechts_innen\"><table class=\"umfeld\">");
        for (String[] row : personRechts) {
            sb.append("<tr><td class=\"name\">").append(esc(row[0])).append("</td><td class=\"eintrag\">").append(esc(row[1])).append("</td></tr>");
        }
        sb.append("</table></div></td></tr></table>\n");

        // Eigenschaften links / Basiswerte rechts
        sb.append("<table class=\"eigenschaften modern-section\" id=\"section-eigenschaften-und-basiswerte-3\">");
        sb.append("<tr><th class=\"titel\" colspan=\"2\">Eigenschaften und Basiswerte</th></tr><tr><td>");
        sb.append("<table class=\"eigenschaften-split\"><tr><td>");
        sb.append("<table class=\"eigenschaften\"><thead><tr><th>Name</th><th>Start</th><th>Mod</th><th>Aktuell</th></tr></thead><tbody>");
        writeEigenschaftsSpalte(sb, props);
        sb.append("</tbody></table></td><td>");
        sb.append("<table class=\"eigenschaften\"><thead><tr><th>Name</th><th>Start</th><th>Mod</th><th>Aktuell</th></tr></thead><tbody>");
        writeBasisSpalte(sb, props);
        sb.append("</tbody></table></td></tr></table></td></tr></table>\n");

        // AP horizontal
        if (hasAp) {
        sb.append("<table class=\"abenteuerpunkte modern-section\" id=\"section-abenteuerpunkte-4\">");
        sb.append("<tr><th class=\"titel\" colspan=\"2\">Abenteuerpunkte</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"ap-horizontal\"><tr>");
        for (String[] row : ap) {
            sb.append("<td><span class=\"name\">").append(esc(row[0])).append("</span><span class=\"eintrag\">").append(esc(row[1])).append("</span></td>");
        }
        sb.append("</tr></table></div></td></tr></table>\n");
        }

        // Vorteile | Nachteile nebeneinander
        if (hasVn) {
        int maxVn = Math.max(vorteile.size(), nachteile.size());
        sb.append("<table class=\"vorteile modern-section\" id=\"section-vorteile-und-nachteile-5\">");
        sb.append("<tr><th class=\"titel\" colspan=\"2\">Vorteile und Nachteile</th></tr>");
        sb.append("<tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"vorteile\"><tr>");
        sb.append("<th class=\"name\">Vorteile</th><th class=\"name\">Nachteile</th></tr>");
        for (int i = 0; i < maxVn; i++) {
            String v = i < vorteile.size() ? vorteile.get(i) : "";
            String n = i < nachteile.size() ? nachteile.get(i) : "";
            sb.append("<tr><td class=\"name\">").append(esc(v)).append("</td><td class=\"name\">").append(esc(n)).append("</td></tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        // SF zwei Spalten
        if (hasSf) {
        sb.append("<table class=\"sonderfertigkeiten modern-section\" id=\"section-sonderfertigkeiten-6\">");
        sb.append("<tr><th class=\"titel\" colspan=\"2\">Sonderfertigkeiten</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\"><table class=\"sf-zwei-spalten\">");
        for (int i = 0; i < sfs.size(); i += 2) {
            sb.append("<tr><td class=\"name\">").append(esc(sfs.get(i))).append("</td>");
            if (i + 1 < sfs.size()) {
                sb.append("<td class=\"name\">").append(esc(sfs.get(i + 1))).append("</td>");
            } else {
                sb.append("<td class=\"name\">&nbsp;</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        // Talente: Untergruppen, zwei Spalten
        if (hasTalente) {
        sb.append("<table class=\"talente modern-section\" id=\"section-talente-7\">");
        sb.append("<tr><th class=\"titel\" colspan=\"6\">Talente</th></tr><tr>");
        List<String> groupOrder = new ArrayList<String>(talentGruppen.keySet());
        int mid = (groupOrder.size() + 1) / 2;
        sb.append("<td class=\"links\"><div class=\"links_innen\">");
        for (int i = 0; i < mid; i++) {
            writeTalentGruppe(sb, groupOrder.get(i), talentGruppen.get(groupOrder.get(i)));
        }
        sb.append("</div></td>");
        sb.append("<td class=\"rechts\"><div class=\"rechts_innen\">");
        for (int i = mid; i < groupOrder.size(); i++) {
            writeTalentGruppe(sb, groupOrder.get(i), talentGruppen.get(groupOrder.get(i)));
        }
        sb.append("</div></td></tr></table>\n");

        }

        // Zauber
        if (hasZauber) {
        sb.append("<table class=\"zauber modern-section\" id=\"section-zauber-8\">");
        sb.append("<tr><th class=\"titel\" colspan=\"8\">Zauber</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        if (zauber.isEmpty() && zauberDebug != null && zauberDebug.length() > 0) {
            sb.append("<!-- keine Zauber geparst; daten-Kinder: ").append(esc(zauberDebug)).append(" -->\n");
        }
        sb.append("<table class=\"zauber gitternetz\"><tr>");
        sb.append("<th class=\"name\">Zauber</th><th class=\"probe\">Probe</th><th class=\"zfw\">ZfW</th>");
        sb.append("<th class=\"rep\">Rep</th><th class=\"merkmale\">Merkmale</th><th class=\"haus\">Haus</th>");
        sb.append("<th class=\"komp\">Komp</th><th class=\"lern\">L-Komp</th></tr>");
        for (Zauber z : zauber) {
            if (z.name == null || z.name.isEmpty()) continue;
            sb.append("<tr class=\"wuerfelziel zauber-wuerfel\" data-name=\"").append(esc(z.name))
              .append("\" data-probe=\"").append(esc(z.probeKurz)).append("\" data-skill-value=\"").append(z.value)
              .append("\" data-skill-label=\"ZfW\" title=\"").append(esc(z.name)).append("\">")
              .append("<td class=\"name\">").append(esc(z.name));
            if (z.variante != null && !z.variante.isEmpty()) sb.append(" (").append(esc(z.variante)).append(")");
            sb.append("</td><td class=\"probe\"> (").append(esc(z.probeKurz)).append(")</td>")
              .append("<td class=\"zfw\">").append(z.value).append("</td>")
              .append("<td class=\"rep\">").append(esc(z.rep)).append("</td>")
              .append("<td class=\"merkmale\">").append(esc(z.merkmale)).append("</td>")
              .append("<td class=\"haus\">").append(z.haus ? "x" : "").append("</td>")
              .append("<td class=\"komp\">").append(esc(z.komp)).append("</td>")
              .append("<td class=\"lern\">").append(esc(z.lern)).append("</td></tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        // Nahkampfwaffen (ausgerüstet)
        if (hasNkw) {
        sb.append("<table class=\"nkwaffen modern-section\" id=\"section-nahkampfwaffen-9\">");
        sb.append("<tr><th class=\"titel\" colspan=\"12\">Nahkampfwaffen</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"nkwaffen gitternetz\"><tr>");
        sb.append("<th class=\"name\">Nahkampfwaffe</th><th class=\"typbe\">Typ/BE</th><th class=\"dk\">DK</th>");
        sb.append("<th class=\"tp\">TP</th><th class=\"tpkk\">TP/KK</th><th class=\"ini\">Ini</th><th class=\"wm\">WM</th>");
        sb.append("<th class=\"at\">AT</th><th class=\"pa\">PA</th><th class=\"efftp\">effTP</th>");
        sb.append("<th class=\"minbf\">min BF</th><th class=\"aktbf\">akt BF</th></tr>");
        for (Waffe w : nkw) {
            sb.append("<tr><td class=\"name\">").append(esc(w.name)).append("</td>")
              .append("<td class=\"typbe\">").append(esc(w.typBe)).append("</td>")
              .append("<td class=\"dk\">").append(esc(w.dk)).append("</td>")
              .append("<td class=\"tp\">").append(esc(w.tp)).append("</td>")
              .append("<td class=\"tpkk\">").append(esc(w.tpkk)).append("</td>")
              .append("<td class=\"ini\">").append(esc(w.ini)).append("</td>")
              .append("<td class=\"wm\">").append(esc(w.wm)).append("</td>")
              .append("<td class=\"at\">").append(esc(w.at)).append("</td>")
              .append("<td class=\"pa\">").append(esc(w.pa)).append("</td>")
              .append("<td class=\"efftp\">").append(esc(w.efftp)).append("</td>")
              .append("<td class=\"minbf\">").append(esc(w.minbf)).append("</td>")
              .append("<td class=\"aktbf\">").append(esc(w.aktbf)).append("</td></tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        // Rüstungen
        if (hasRuestung) {
        sb.append("<table class=\"ruestungen modern-section\" id=\"section-ruestungen-11\">");
        sb.append("<tr><th class=\"titel\" colspan=\"14\">Rüstungen</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"zonenruestungen gitternetz\"><tr>");
        sb.append("<th class=\"name\">Name</th>");
        sb.append("<th class=\"ko\">Ko</th><th class=\"br\">Br</th><th class=\"rue\">Rü</th><th class=\"ba\">Ba</th>");
        sb.append("<th class=\"la\">LA</th><th class=\"ra\">RA</th><th class=\"lb\">LB</th><th class=\"rb\">RB</th>");
        sb.append("<th class=\"ges\">Ges</th><th class=\"grs\">gRS</th><th class=\"gbe\">gBE</th>");
        sb.append("<th class=\"rs\">RS</th><th class=\"be\">BE</th></tr>");
        String gesamtBe = "";
        String lastGrs = "", lastGbe = "", lastRs = "";
        for (Ruestung r : ruestungen) {
            sb.append("<tr>");
            sb.append("<td class=\"name\">").append(esc(r.name)).append("</td>");
            sb.append("<td class=\"ko\">").append(esc(r.kopf)).append("</td>");
            sb.append("<td class=\"br\">").append(esc(r.brust)).append("</td>");
            sb.append("<td class=\"rue\">").append(esc(r.ruecken)).append("</td>");
            sb.append("<td class=\"ba\">").append(esc(r.bauch)).append("</td>");
            sb.append("<td class=\"la\">").append(esc(r.linkerarm)).append("</td>");
            sb.append("<td class=\"ra\">").append(esc(r.rechterarm)).append("</td>");
            sb.append("<td class=\"lb\">").append(esc(r.linkesbein)).append("</td>");
            sb.append("<td class=\"rb\">").append(esc(r.rechtesbein)).append("</td>");
            sb.append("<td class=\"ges\">").append(esc(r.gesamt)).append("</td>");
            sb.append("<td class=\"grs\">").append(esc(r.grs)).append("</td>");
            sb.append("<td class=\"gbe\">").append(esc(r.gbe)).append("</td>");
            sb.append("<td class=\"rs\">").append(esc(r.rs)).append("</td>");
            sb.append("<td class=\"be\">").append(esc(r.be)).append("</td>");
            sb.append("</tr>");
            if (r.be != null && r.be.length() > 0) gesamtBe = r.be;
            if (r.grs != null && r.grs.length() > 0) lastGrs = r.grs;
            if (r.gbe != null && r.gbe.length() > 0) lastGbe = r.gbe;
            if (r.rs != null && r.rs.length() > 0) lastRs = r.rs;
        }
        // Gesamt-Zeile (für Würfel-JS BE-Abzug)
        sb.append("<tr><td class=\"name\">Gesamt</td>");
        sb.append("<td class=\"ko\"></td><td class=\"br\"></td><td class=\"rue\"></td><td class=\"ba\"></td>");
        sb.append("<td class=\"la\"></td><td class=\"ra\"></td><td class=\"lb\"></td><td class=\"rb\"></td>");
        sb.append("<td class=\"ges\"></td>");
        sb.append("<td class=\"grs\">").append(esc(lastGrs)).append("</td>");
        sb.append("<td class=\"gbe\">").append(esc(lastGbe.isEmpty() ? gesamtBe : lastGbe)).append("</td>");
        sb.append("<td class=\"rs\">").append(esc(lastRs)).append("</td>");
        sb.append("<td class=\"be\">").append(esc(gesamtBe)).append("</td></tr>");
        sb.append("</table></div></td></tr></table>\n");
        }

        // Schilde / Paradewaffen
        if (hasSchild) {
        sb.append("<table class=\"schilde modern-section\" id=\"section-schilder-12\">");
        sb.append("<tr><th class=\"titel\" colspan=\"7\">Schilde</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"schilde gitternetz\"><tr>");
        sb.append("<th class=\"name\">Name</th><th class=\"typ\">TYP</th><th class=\"ini\">INI</th>");
        sb.append("<th class=\"wm\">WM</th><th class=\"pa\">PA</th>");
        sb.append("<th class=\"minbf\">minBF</th><th class=\"aktbf\">aktBF</th></tr>");
        for (Schild s : schilder) {
            sb.append("<tr>");
            sb.append("<td class=\"name\">").append(esc(s.name)).append("</td>");
            sb.append("<td class=\"typ\">").append(esc(s.typ.isEmpty() ? "Schild" : s.typ)).append("</td>");
            sb.append("<td class=\"ini\">").append(esc(s.ini)).append("</td>");
            sb.append("<td class=\"wm\">").append(esc(s.mod)).append("</td>");
            sb.append("<td class=\"pa\">").append(esc(s.pa)).append("</td>");
            sb.append("<td class=\"minbf\">").append(esc(s.minbf)).append("</td>");
            sb.append("<td class=\"aktbf\">").append(esc(s.aktbf)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        // Inventar 2-spaltig
        if (hasItems) {
        sb.append("<table class=\"inventar modern-section\" id=\"section-inventar-10\">");
        sb.append("<tr><th class=\"titel\" colspan=\"6\">Inventar</th></tr><tr><td class=\"mitte\"><div class=\"mitte_innen\">");
        sb.append("<table class=\"inventar gitternetz\"><tr>");
        sb.append("<th class=\"name\">Gegenstand</th><th class=\"anzahl\">Anzahl</th><th class=\"gewicht\">Gewicht</th>");
        sb.append("<th class=\"name\">Gegenstand</th><th class=\"anzahl\">Anzahl</th><th class=\"gewicht\">Gewicht</th></tr>");
        for (int i = 0; i < items.size(); i += 2) {
            Item a = items.get(i);
            Item b = (i + 1 < items.size()) ? items.get(i + 1) : null;
            sb.append("<tr><td class=\"name\">").append(esc(a.name)).append("</td>")
              .append("<td class=\"anzahl\">").append(esc(a.anzahl)).append("</td>")
              .append("<td class=\"gewicht\">").append(esc(a.gewicht)).append("</td>");
            if (b != null) {
                sb.append("<td class=\"name\">").append(esc(b.name)).append("</td>")
                  .append("<td class=\"anzahl\">").append(esc(b.anzahl)).append("</td>")
                  .append("<td class=\"gewicht\">").append(esc(b.gewicht)).append("</td>");
            } else {
                sb.append("<td class=\"name\">&nbsp;</td><td class=\"anzahl\">&nbsp;</td><td class=\"gewicht\">&nbsp;</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></div></td></tr></table>\n");
        }

        sb.append("<button type=\"button\" id=\"roll-log-toggle\" class=\"roll-log-toggle\" title=\"Würfellog öffnen\" aria-label=\"Würfellog öffnen\">📜</button>\n");
        sb.append("<script type=\"text/javascript\" id=\"modern-theme-and-dice-script\">\n");
        sb.append(DiceJavascript.SCRIPT);
        sb.append("\n</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static void writeEigenschaftsSpalte(StringBuilder sb, Map<String, String[]> props) {
        String[][] eig = {
            {"mut","Mut","MU"},{"klugheit","Klugheit","KL"},{"intuition","Intuition","IN"},
            {"charisma","Charisma","CH"},{"fingerfertigkeit","Fingerfertigkeit","FF"},
            {"gewandtheit","Gewandtheit","GE"},{"konstitution","Konstitution","KO"},
            {"körperkraft","Körperkraft","KK"},{"koerperkraft","Körperkraft","KK"}
        };
        HashSet<String> seen = new HashSet<String>();
        for (String[] e : eig) {
            if (seen.contains(e[2])) continue;
            String[] v = props.get(e[0]);
            if (v == null) continue;
            seen.add(e[2]);
            sb.append("<tr class=\"wuerfelziel eigenschaft-wuerfel\" data-eigenschaft=\"").append(e[2])
              .append("\" data-name=\"").append(esc(e[1])).append("\" data-wert=\"").append(esc(v[0])).append("\">")
              .append("<td class=\"name\">").append(esc(e[1])).append("</td><td>").append(esc(v[1]))
              .append("</td><td class=\"modifikator\">").append(esc(v[2]))
              .append("</td><td class=\"aktuell\">").append(esc(v[0])).append("</td></tr>");
        }
        String[] gs = props.get("geschwindigkeit");
        if (gs != null) {
            sb.append("<tr><td class=\"name\">Geschwindigkeit</td><td>").append(esc(gs[1]))
              .append("</td><td class=\"modifikator\">").append(esc(gs[2]))
              .append("</td><td class=\"aktuell\">").append(esc(gs[0])).append("</td></tr>");
        }
    }

    private static void writeBasisSpalte(StringBuilder sb, Map<String, String[]> props) {
        String[][] basis = {
            {"lebensenergie","Lebensenergie"},{"ausdauer","Ausdauer"},{"astralenergie","Astralenergie"},
            {"karmaenergie","Karmaenergie"},{"magieresistenz","Magieresistenz"},{"initiative","Initiative"},
            {"attacke","AT"},{"parade","PA"},{"fernkampf-basis","FK"},
            {"wundschwelle","Wundschwelle"}
        };
        for (String[] b : basis) {
            String[] v = props.get(b[0]);
            if (v == null) continue;
            boolean ini = "initiative".equals(b[0]);
            sb.append("<tr");
            if (ini) sb.append(" class=\"wuerfelziel initiative-wuerfel\" data-wert=\"").append(esc(v[0])).append("\"");
            sb.append("><td class=\"name\">").append(esc(b[1])).append("</td><td>").append(esc(v[1]))
              .append("</td><td class=\"modifikator\">").append(esc(v[2]))
              .append("</td><td class=\"aktuell\">").append(esc(v[0])).append("</td></tr>");
        }
    }

    private static void writeTalentGruppe(StringBuilder sb, String gruppe, List<Talent> list) {
        if (list == null || list.isEmpty()) return;
        boolean kampf = "Kampf".equalsIgnoreCase(gruppe) || "Kampftechniken".equalsIgnoreCase(gruppe);
        sb.append("<table class=\"talentgruppe gitternetz\">");
        if (kampf) {
            // Kampftechniken: keine Talentproben-Würfel (AT/PA-Werte, keine 3W20-Probe)
            sb.append("<tr><th class=\"name\" colspan=\"2\">").append(esc(gruppe.isEmpty() ? "Kampftechniken" : gruppe))
              .append("</th><th class=\"be\">BE</th><th class=\"at\">AT</th><th class=\"pa\">PA</th><th class=\"taw\">TaW</th></tr>");
            for (Talent t : list) {
                sb.append("<tr><td class=\"name\">").append(esc(t.name)).append("</td><td class=\"stk\">").append(esc(t.komp))
                  .append("</td><td class=\"be\">").append(esc(t.be)).append("</td><td class=\"at\">").append(esc(t.at))
                  .append("</td><td class=\"pa\">").append(esc(t.pa)).append("</td><td class=\"taw\">").append(t.value).append("</td></tr>");
            }
        } else {
            sb.append("<tr><th class=\"name\" colspan=\"3\">").append(esc(gruppe)).append("</th><th class=\"taw\">TaW</th></tr>");
            for (Talent t : list) {
                if (t.probeKurz.isEmpty()) {
                    sb.append("<tr><td class=\"name\" colspan=\"3\">").append(esc(t.name)).append("</td><td class=\"taw\">").append(t.value).append("</td></tr>");
                    continue;
                }
                sb.append("<tr class=\"wuerfelziel talent-wuerfel\" data-name=\"").append(esc(t.name))
                  .append("\" data-probe=\"").append(esc(t.probeKurz)).append("\" data-skill-value=\"").append(t.value)
                  .append("\" data-skill-label=\"TaW\"");
                if (t.be != null && !t.be.isEmpty()) sb.append(" data-be=\"").append(esc(t.be)).append("\"");
                sb.append("><td class=\"name\">").append(esc(t.name)).append("</td><td class=\"probe\">").append(esc(t.probeKurz))
                  .append("</td><td class=\"be\">").append(esc(t.be)).append("</td><td class=\"taw\">").append(t.value).append("</td></tr>");
            }
        }
        sb.append("</table>\n");
    }

    private static Map<String, String[]> readEig(Element daten) {
        Map<String, String[]> map = new LinkedHashMap<String, String[]>();
        Element eig = child(daten, "eigenschaften");
        if (eig == null) return map;
        NodeList kids = eig.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element e = (Element) kids.item(i);
            map.put(e.getTagName().toLowerCase(), new String[]{text(e, "akt"), text(e, "start"), text(e, "modi")});
        }
        return map;
    }

    private static List<String[]> readRKP(Element angaben) {
        List<String[]> rows = new ArrayList<String[]>();
        if (angaben == null) return rows;
        add(rows, "Rasse", text(angaben, "rasse"));
        add(rows, "Kultur", text(angaben, "kultur"));
        Element prof = child(angaben, "profession");
        add(rows, "Profession", text(prof, "text"));
        return rows;
    }

    private static List<String[]> readBeschreibung(Element angaben) {
        List<String[]> rows = new ArrayList<String[]>();
        if (angaben == null) return rows;
        add(rows, "Geschlecht", text(angaben, "geschlecht"));
        add(rows, "Geburtstag", text(angaben, "geburtstag"));
        String gr = text(angaben, "groesse");
        if (!gr.isEmpty() && !gr.contains("Halbfinger")) gr = gr + " Halbfinger";
        add(rows, "Größe", gr);
        String ge = text(angaben, "gewicht");
        if (!ge.isEmpty() && !ge.contains("Stein")) ge = ge + " Stein";
        add(rows, "Gewicht", ge);
        add(rows, "Haarfarbe", text(angaben, "haarfarbe"));
        add(rows, "Augenfarbe", text(angaben, "augenfarbe"));
        return rows;
    }

    private static List<String[]> readBeschreibungRechts(Element angaben, Map<String, String[]> props) {
        List<String[]> rows = new ArrayList<String[]>();
        if (angaben == null) return rows;
        rows.add(new String[]{"Stand", text(angaben, "stand")});
        rows.add(new String[]{"Titel", text(angaben, "titel")});
        String so = "";
        if (props != null && props.get("sozialstatus") != null) so = props.get("sozialstatus")[0];
        if (so.isEmpty()) so = text(angaben, "sozialstatus");
        rows.add(new String[]{"Sozialstatus", so});
        // Familie / Herkunft / Hintergrund
        Element fam = child(angaben, "familie");
        String familie = "";
        if (fam != null) {
            familie = text(fam, "text");
            if (familie.isEmpty()) {
                StringBuilder fb = new StringBuilder();
                for (int i = 0; i <= 5; i++) {
                    String part = text(fam, "f" + i);
                    if (!part.isEmpty()) {
                        if (fb.length() > 0) fb.append(" ");
                        fb.append(part);
                    }
                }
                familie = fb.toString();
            }
        }
        Element aus = child(angaben, "aussehen");
        String aussehen = "";
        if (aus != null) {
            aussehen = text(aus, "text");
            if (aussehen.isEmpty()) {
                StringBuilder ab = new StringBuilder();
                for (int i = 0; i <= 3; i++) {
                    String part = text(aus, "a" + i);
                    if (!part.isEmpty()) {
                        if (ab.length() > 0) ab.append(" ");
                        ab.append(part);
                    }
                }
                aussehen = ab.toString();
            }
        }
        Element notiz = child(angaben, "notizen");
        String notizen = "";
        if (notiz != null) {
            notizen = text(notiz, "text");
        }
        String combo = familie;
        if (!aussehen.isEmpty()) {
            if (!combo.isEmpty()) combo += "\n";
            combo += aussehen;
        }
        if (!notizen.isEmpty()) {
            if (!combo.isEmpty()) combo += "\n";
            combo += notizen;
        }
        if (!combo.isEmpty()) {
            rows.add(new String[]{"Familie / Herkunft", combo});
        }
        return rows;
    }

    private static List<String[]> readAp(Element angaben) {
        List<String[]> rows = new ArrayList<String[]>();
        if (angaben == null) return rows;
        Element ap = child(angaben, "ap");
        if (ap != null) {
            add(rows, "AP gesamt", text(ap, "gesamt"));
            add(rows, "AP frei", text(ap, "frei"));
            add(rows, "AP genutzt", text(ap, "genutzt"));
        }
        return rows;
    }

    private static void add(List<String[]> rows, String k, String v) {
        if (v != null && !v.isEmpty()) rows.add(new String[]{k, v});
    }

    private static void readVorteileNachteile(Element daten, List<String> vorteile, List<String> nachteile) {
        Element vt = child(daten, "vorteile");
        if (vt == null) return;
        NodeList nodes = vt.getElementsByTagName("vorteil");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element v = (Element) nodes.item(i);
            String n = text(v, "namemitkommentar");
            if (n.isEmpty()) n = text(v, "name");
            if (n.isEmpty()) continue;
            String istV = text(v, "istvorteil");
            String istN = text(v, "istnachteil");
            if ("true".equalsIgnoreCase(istN)) nachteile.add(n);
            else if ("true".equalsIgnoreCase(istV)) vorteile.add(n);
            else {
                // Fallback: unbekannt → Vorteile
                vorteile.add(n);
            }
        }
    }

    private static List<String> readSf(Element daten) {
        List<String> list = new ArrayList<String>();
        Element sf = child(daten, "sonderfertigkeiten");
        if (sf == null) return list;
        NodeList nodes = sf.getElementsByTagName("sonderfertigkeit");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element s = (Element) nodes.item(i);
            String n = text(s, "namemitkommentar");
            if (n.isEmpty()) n = text(s, "name");
            if (!n.isEmpty()) list.add(n);
        }
        return list;
    }

    private static Map<String, List<Talent>> readTalenteGruppiert(Element daten) {
        Map<String, List<Talent>> map = new LinkedHashMap<String, List<Talent>>();
        Element tl = child(daten, "talentliste");
        if (tl == null) tl = child(daten, "talente");
        if (tl == null) return map;
        NodeList nodes = tl.getElementsByTagName("talent");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element t = (Element) nodes.item(i);
            Talent talent = new Talent();
            talent.name = text(t, "name");
            talent.probe = text(t, "probe");
            talent.probeKurz = normalizeProbe(talent.probe);
            talent.be = text(t, "behinderung");
            talent.at = text(t, "at");
            talent.pa = text(t, "pa");
            talent.komp = text(t, "komplexität");
            if (talent.komp.isEmpty()) talent.komp = text(t, "komplexitaet");
            String bereich = text(t, "bereich");
            if (bereich.isEmpty()) bereich = "Sonstige";
            try { talent.value = Integer.parseInt(text(t, "wert")); } catch (Exception e) { talent.value = 0; }
            if (talent.name.isEmpty()) continue;
            if (!map.containsKey(bereich)) map.put(bereich, new ArrayList<Talent>());
            map.get(bereich).add(talent);
        }
        return map;
    }

    private static List<Zauber> readZauber(Element daten) {
        List<Zauber> list = new ArrayList<Zauber>();
        // Offizielle API (version 2): zauberliste; einige Server: zauber
        Element zl = child(daten, "zauberliste");
        if (zl == null) zl = child(daten, "zauber");
        if (zl == null) {
            NodeList any = daten.getElementsByTagNameNS("*", "zauberliste");
            if (any.getLength() == 0) any = daten.getElementsByTagName("zauberliste");
            if (any.getLength() > 0) zl = (Element) any.item(0);
        }
        if (zl == null) {
            NodeList any = daten.getElementsByTagNameNS("*", "zauber");
            if (any.getLength() == 0) any = daten.getElementsByTagName("zauber");
            for (int i = 0; i < any.getLength(); i++) {
                parseOneZauber((Element) any.item(i), list);
            }
            return list;
        }
        NodeList kids = zl.getChildNodes();
        int added = 0;
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element el = (Element) kids.item(i);
            String ln = localName(el).toLowerCase();
            if ("zauber".equals(ln) || text(el, "name").length() > 0 || attr(el, "name", "").length() > 0) {
                if (parseOneZauber(el, list)) added++;
            }
        }
        if (added == 0) {
            NodeList nodes = zl.getElementsByTagNameNS("*", "zauber");
            if (nodes.getLength() == 0) nodes = zl.getElementsByTagName("zauber");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element z = (Element) nodes.item(i);
                if (z != zl) parseOneZauber(z, list);
            }
        }
        return list;
    }

    private static boolean parseOneZauber(Element z, List<Zauber> list) {
        Zauber zauber = new Zauber();
        zauber.name = firstNonEmpty(text(z, "name"), text(z, "namemitvariante"),
                text(z, "nameausfuehrlich"), attr(z, "name", ""));
        if (zauber.name.isEmpty()) return false;
        zauber.probe = firstNonEmpty(text(z, "probe"), attr(z, "probe", ""));
        zauber.probeKurz = normalizeProbe(zauber.probe);
        zauber.variante = firstNonEmpty(text(z, "variante"), attr(z, "variante", ""));
        zauber.rep = firstNonEmpty(text(z, "repräsentation"), text(z, "repraesentation"),
                text(z, "rep"), attr(z, "repraesentation", ""), attr(z, "rep", ""));
        zauber.merkmale = firstNonEmpty(text(z, "merkmale"), attr(z, "merkmale", ""));
        zauber.komp = firstNonEmpty(text(z, "komplexität"), text(z, "komplexitaet"),
                attr(z, "k", ""), attr(z, "komplexität", ""));
        zauber.lern = firstNonEmpty(text(z, "lernkomplexität"), text(z, "lernkomplexitaet"),
                attr(z, "lernkomplexität", ""));
        String haus = firstNonEmpty(text(z, "hauszauber"), text(z, "hauszauberformatiert"),
                attr(z, "hauszauber", ""));
        zauber.haus = "true".equalsIgnoreCase(haus) || "x".equalsIgnoreCase(haus) || "X".equals(haus);
        String wert = firstNonEmpty(text(z, "wert"), attr(z, "value", ""), attr(z, "wert", "0"));
        try { zauber.value = Integer.parseInt(wert); } catch (Exception e) { zauber.value = 0; }
        list.add(zauber);
        return true;
    }

    private static List<Waffe> readNahkampf(Element daten) {
        List<Waffe> list = new ArrayList<Waffe>();
        Element nk = child(daten, "nahkampfwaffen");
        if (nk == null) return list;
        NodeList nodes = nk.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) continue;
            Element e = (Element) nodes.item(i);
            Waffe w = new Waffe();
            w.name = firstNonEmpty(text(e, "name"), text(e, "waffenname"), attr(e, "name", ""));
            w.typBe = firstNonEmpty(text(e, "typbe"), text(e, "typ"), text(e, "talent"));
            String be = text(e, "be");
            if (!be.isEmpty() && w.typBe.indexOf("BE") < 0) w.typBe = (w.typBe + " / " + be).trim();
            w.dk = text(e, "dk");
            w.tp = firstNonEmpty(text(e, "tp"), text(e, "trefferpunkte"));
            w.tpkk = firstNonEmpty(text(e, "tpkk"), text(e, "tp/kk"));
            w.ini = text(e, "ini");
            w.wm = firstNonEmpty(text(e, "wm"), text(e, "waffenmodifikator"));
            w.at = text(e, "at");
            w.pa = text(e, "pa");
            w.efftp = firstNonEmpty(text(e, "efftp"), text(e, "effektivetp"));
            w.minbf = firstNonEmpty(text(e, "minbf"), text(e, "bfmin"));
            w.aktbf = firstNonEmpty(text(e, "aktbf"), text(e, "bfakt"), text(e, "bf"));
            if (!w.name.isEmpty()) list.add(w);
        }
        return list;
    }


    private static List<Ruestung> readRuestungen(Element daten) {
        List<Ruestung> list = new ArrayList<Ruestung>();
        collectRuestungen(child(daten, "ruestungen"), list);
        if (list.isEmpty()) {
            // nur wenn top-level leer: kampfsets prüfen (sonst Duplikate)
            Element ks = child(daten, "kampfsets");
            if (ks != null) {
                NodeList sets = ks.getChildNodes();
                for (int i = 0; i < sets.getLength(); i++) {
                    if (!(sets.item(i) instanceof Element)) continue;
                    Element set = (Element) sets.item(i);
                    collectRuestungen(child(set, "ruestungen"), list);
                    Element einfach = child(set, "ruestungeinfach");
                    if (einfach != null) {
                        Ruestung r = parseRuestung(einfach);
                        if (r != null && !r.name.isEmpty()) list.add(r);
                    }
                    // ein aktives Set reicht
                    if (!list.isEmpty()) break;
                }
            }
        }
        return dedupeRuestungen(list);
    }

    private static List<Ruestung> dedupeRuestungen(List<Ruestung> list) {
        List<Ruestung> out = new ArrayList<Ruestung>();
        HashSet<String> seen = new HashSet<String>();
        for (Ruestung r : list) {
            if (r.name == null || r.name.isEmpty()) continue;
            String key = r.name.trim().toLowerCase();
            if (seen.contains(key)) continue;
            seen.add(key);
            out.add(r);
        }
        return out;
    }

    private static void collectRuestungen(Element parent, List<Ruestung> list) {
        if (parent == null) return;
        int before = list.size();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element el = (Element) kids.item(i);
            String ln = localName(el).toLowerCase();
            if (!"ruestung".equals(ln) && !ln.endsWith("ruestung")) continue;
            Ruestung r = parseRuestung(el);
            if (r != null && !r.name.isEmpty()) list.add(r);
        }
        if (list.size() == before) {
            NodeList nodes = parent.getElementsByTagName("ruestung");
            for (int i = 0; i < nodes.getLength(); i++) {
                Ruestung r = parseRuestung((Element) nodes.item(i));
                if (r != null && !r.name.isEmpty()) list.add(r);
            }
        }
    }

    private static Ruestung parseRuestung(Element el) {
        Ruestung r = new Ruestung();
        r.name = firstNonEmpty(text(el, "name"), attr(el, "name", ""));
        r.rs = firstNonEmpty(text(el, "rs"), text(el, "gesamtzonenschutz"), attr(el, "rs", ""));
        r.be = firstNonEmpty(text(el, "be"), text(el, "behinderung"), attr(el, "be", ""));
        r.kopf = firstNonEmpty(text(el, "kopf"), attr(el, "kopf", ""));
        r.brust = firstNonEmpty(text(el, "brust"), attr(el, "brust", ""));
        r.ruecken = firstNonEmpty(text(el, "ruecken"), text(el, "rücken"), attr(el, "ruecken", ""));
        r.bauch = firstNonEmpty(text(el, "bauch"), attr(el, "bauch", ""));
        r.linkerarm = firstNonEmpty(text(el, "linkerarm"), attr(el, "linkerarm", ""));
        r.rechterarm = firstNonEmpty(text(el, "rechterarm"), attr(el, "rechterarm", ""));
        r.linkesbein = firstNonEmpty(text(el, "linkesbein"), attr(el, "linkesbein", ""));
        r.rechtesbein = firstNonEmpty(text(el, "rechtesbein"), attr(el, "rechtesbein", ""));
        r.gesamt = firstNonEmpty(text(el, "gesamt"), attr(el, "gesamt", ""));
        r.grs = firstNonEmpty(text(el, "gesamtzonenschutz"), text(el, "grs"), text(el, "gers"), attr(el, "grs", ""));
        r.gbe = firstNonEmpty(text(el, "behinderung"), text(el, "gbe"), attr(el, "gbe", ""), r.be);
        if (r.rs.isEmpty()) r.rs = firstNonEmpty(r.grs, r.gesamt);
        if (r.be.isEmpty()) r.be = r.gbe;
        return r;
    }

    private static List<Schild> readSchilder(Element daten) {
        List<Schild> list = new ArrayList<Schild>();
        collectSchilder(child(daten, "schilder"), list);
        if (list.isEmpty()) {
            Element ks = child(daten, "kampfsets");
            if (ks != null) {
                NodeList sets = ks.getChildNodes();
                for (int i = 0; i < sets.getLength(); i++) {
                    if (!(sets.item(i) instanceof Element)) continue;
                    collectSchilder(child((Element) sets.item(i), "schilder"), list);
                    if (!list.isEmpty()) break;
                }
            }
        }
        return dedupeSchilder(list);
    }

    private static List<Schild> dedupeSchilder(List<Schild> list) {
        List<Schild> out = new ArrayList<Schild>();
        HashSet<String> seen = new HashSet<String>();
        for (Schild s : list) {
            if (s.name == null || s.name.isEmpty()) continue;
            String key = s.name.trim().toLowerCase();
            if (seen.contains(key)) continue;
            seen.add(key);
            out.add(s);
        }
        return out;
    }

    private static void collectSchilder(Element parent, List<Schild> list) {
        if (parent == null) return;
        int before = list.size();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element el = (Element) kids.item(i);
            String ln = localName(el).toLowerCase();
            if (!"schild".equals(ln) && !ln.contains("schild")) continue;
            Schild s = parseSchild(el);
            if (s != null && !s.name.isEmpty()) list.add(s);
        }
        if (list.size() == before) {
            NodeList nodes = parent.getElementsByTagName("schild");
            for (int i = 0; i < nodes.getLength(); i++) {
                Schild s = parseSchild((Element) nodes.item(i));
                if (s != null && !s.name.isEmpty()) list.add(s);
            }
        }
    }

    private static Schild parseSchild(Element el) {
        Schild s = new Schild();
        s.name = firstNonEmpty(text(el, "name"), attr(el, "name", ""));
        s.typ = firstNonEmpty(text(el, "typ"), attr(el, "typ", ""));
        s.pa = firstNonEmpty(text(el, "pa"), attr(el, "pa", ""));
        s.ini = firstNonEmpty(text(el, "ini"), attr(el, "ini", ""));
        s.mod = firstNonEmpty(text(el, "mod"), text(el, "wm"), attr(el, "mod", ""));
        s.minbf = firstNonEmpty(text(el, "bfmin"), text(el, "minbf"), attr(el, "bfmin", ""));
        s.aktbf = firstNonEmpty(text(el, "bfakt"), text(el, "aktbf"), attr(el, "bfakt", ""));
        s.bf = firstNonEmpty(text(el, "bf"), attr(el, "bf", ""));
        return s;
    }


    private static List<Item> readItems(Element daten) {
        List<Item> list = new ArrayList<Item>();
        Element g = child(daten, "gegenstaende");
        if (g == null) return list;
        NodeList nodes = g.getElementsByTagName("gegenstand");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            Item item = new Item();
            item.name = firstNonEmpty(text(el, "name"), attr(el, "name", ""));
            item.anzahl = firstNonEmpty(text(el, "anzahl"), attr(el, "anzahl", "1"));
            item.gewicht = firstNonEmpty(text(el, "gewicht"), text(el, "weight"), "");
            if (!item.name.isEmpty()) list.add(item);
        }
        return list;
    }

    /**
     * DSA-Regel für Elfen-Repräsentation: Ist die REP eines Zaubers "Elf"
     * und IN &gt; KL, wird in der Probe einmal KL durch IN ersetzt.
     */
    private static void applyElfRep(List<Zauber> zauber, Map<String, String[]> props) {
        int kl = akt(props, "klugheit");
        int inn = akt(props, "intuition");
        if (!(inn > kl)) return;
        for (Zauber z : zauber) {
            String rep = z.rep != null ? z.rep.trim() : "";
            if (!"elf".equalsIgnoreCase(rep)) continue;
            String[] p = z.probeKurz.split("/");
            if (p.length != 3) continue;
            int inC = 0;
            for (int i = 0; i < p.length; i++) {
                if ("IN".equals(p[i])) inC++;
            }
            for (int i = 0; i < p.length; i++) {
                if ("KL".equals(p[i]) && inC < 2) {
                    p[i] = "IN";
                    z.probeKurz = p[0] + "/" + p[1] + "/" + p[2];
                    // Anzeige-Probe ebenfalls anpassen
                    if (z.probe != null && z.probe.length() > 0) {
                        z.probe = z.probeKurz;
                    }
                    break;
                }
            }
        }
    }

    private static int akt(Map<String, String[]> props, String key) {
        String[] v = props.get(key);
        if (v == null || v[0].isEmpty()) return 0;
        try { return Integer.parseInt(v[0]); } catch (Exception e) { return 0; }
    }

    private static Element findRoot(Document doc, String tag) {
        Element r = doc.getDocumentElement();
        if (r != null && tag.equalsIgnoreCase(localName(r))) return r;
        NodeList list = doc.getElementsByTagNameNS("*", tag);
        if (list.getLength() > 0) return (Element) list.item(0);
        list = doc.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String localName(Node n) {
        if (n == null) return "";
        String ln = n.getLocalName();
        if (ln != null && !ln.isEmpty()) return ln;
        String nn = n.getNodeName();
        int c = nn.indexOf(':');
        return c >= 0 ? nn.substring(c + 1) : nn;
    }

    private static Element child(Element parent, String tag) {
        if (parent == null) return null;
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n instanceof Element && tag.equalsIgnoreCase(localName(n))) return (Element) n;
        }
        // namespace-aware deep search for first match by local name
        list = parent.getElementsByTagNameNS("*", tag);
        if (list.getLength() > 0) return (Element) list.item(0);
        list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String text(Element parent, String tag) {
        if (parent == null) return "";
        if (tag == null) {
            String tx = parent.getTextContent();
            return tx != null ? tx.trim() : "";
        }
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n instanceof Element && tag.equalsIgnoreCase(localName(n))) {
                String tx = n.getTextContent();
                return tx != null ? tx.trim() : "";
            }
        }
        return "";
    }

    private static String attr(Element el, String name, String def) {
        if (el == null || !el.hasAttribute(name)) return def;
        return el.getAttribute(name);
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private static String normalizeProbe(String probe) {
        if (probe == null) return "";
        String u = probe.toUpperCase().replace("Ä","A").replace("Ö","O").replace("Ü","U");
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("MU|KL|IN|CH|FF|GE|KO|KK").matcher(u);
        int c = 0;
        while (m.find() && c < 3) {
            if (c > 0) out.append('/');
            out.append(m.group());
            c++;
        }
        return out.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    
    /** Datei-Export-Format &lt;helden&gt;/&lt;held&gt; (Attribute statt Kind-Elemente). */
    private static String generateFromHeld(Element held) {
        String name = attr(held, "name", "Held");
        Element basis = child(held, "basis");
        Element eigEl = child(held, "eigenschaften");
        Map<String, String[]> props = readEigHeld(eigEl);
        List<String[]> rkp = readRKPHeld(basis);
        List<String[]> person = readBeschreibungHeld(basis);
        List<String[]> personRechts = readBeschreibungRechtsHeld(basis, props);
        List<String[]> ap = readApHeld(basis);
        List<String> vorteile = new ArrayList<String>();
        List<String> nachteile = new ArrayList<String>();
        readVtHeld(child(held, "vt"), vorteile, nachteile);
        List<String> sfs = readSfHeld(child(held, "sf"));
        Map<String, List<Talent>> talentGruppen = readTalenteHeld(child(held, "talentliste"));
        List<Zauber> zauber = readZauberHeld(child(held, "zauberliste"));
        List<Waffe> nkw = readNahkampfHeld(held);
        List<Item> items = readItemsHeld(child(held, "gegenstände"));
        if (items.isEmpty()) items = readItemsHeld(child(held, "gegenstaende"));
        String portrait = "";
        if (basis != null) {
            Element por = child(basis, "portraet");
            if (por != null) portrait = attr(por, "value", "");
        }
        // Elfen-Repräsentation: pro Zauber KL→IN wenn REP=Elf und IN>KL
        applyElfRep(zauber, props);

        String zauberDebug = zauber.isEmpty() ? "held-format" : "";
        List<Ruestung> ruestungen = readRuestungenHeld(held);
        List<Schild> schilder = readSchilderHeld(held);
        return renderSheet(name, props, rkp, person, personRechts, ap, vorteile, nachteile, sfs,
                talentGruppen, zauber, nkw, ruestungen, schilder, items, portrait, zauberDebug);
    }

    private static Map<String, String[]> readEigHeld(Element eig) {
        Map<String, String[]> map = new LinkedHashMap<String, String[]>();
        if (eig == null) return map;
        NodeList list = eig.getElementsByTagName("eigenschaft");
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            String n = attr(e, "name", "").toLowerCase();
            String val = attr(e, "value", "");
            String start = attr(e, "startwert", "");
            String mod = attr(e, "mod", "0");
            // Mapping Datei-Namen
            if ("ini".equals(n)) n = "initiative";
            if ("at".equals(n)) n = "attacke";
            if ("pa".equals(n)) n = "parade";
            if ("fk".equals(n)) n = "fernkampf-basis";
            if (("lebensenergie".equals(n) || "ausdauer".equals(n) || "astralenergie".equals(n)
                    || "karmaenergie".equals(n) || "magieresistenz".equals(n))
                    && ("0".equals(val) || val.isEmpty()) && !mod.isEmpty()) {
                // oft value=0 und echter Bonus in mod – Anzeige: value wenn >0 sonst mod
            }
            if (!"0".equals(val) && !val.isEmpty()) {
                map.put(n, new String[]{val, start, mod});
            } else {
                map.put(n, new String[]{mod, start, mod});
            }
        }
        return map;
    }

    private static List<String[]> readRKPHeld(Element basis) {
        List<String[]> rows = new ArrayList<String[]>();
        if (basis == null) return rows;
        Element rasse = child(basis, "rasse");
        if (rasse != null) add(rows, "Rasse", attr(rasse, "string", attr(rasse, "name", "")));
        Element kultur = child(basis, "kultur");
        if (kultur != null) add(rows, "Kultur", attr(kultur, "string", ""));
        Element ausb = child(basis, "ausbildungen");
        if (ausb != null) {
            NodeList list = ausb.getElementsByTagName("ausbildung");
            StringBuilder pb = new StringBuilder();
            for (int i = 0; i < list.getLength(); i++) {
                Element a = (Element) list.item(i);
                String s = attr(a, "string", "");
                if (!s.isEmpty()) {
                    if (pb.length() > 0) pb.append(", ");
                    pb.append(s);
                }
            }
            add(rows, "Profession", pb.toString());
        }
        return rows;
    }

    private static List<String[]> readBeschreibungHeld(Element basis) {
        List<String[]> rows = new ArrayList<String[]>();
        if (basis == null) return rows;
        Element g = child(basis, "geschlecht");
        add(rows, "Geschlecht", attr(g, "name", ""));
        Element rasse = child(basis, "rasse");
        Element aus = rasse != null ? child(rasse, "aussehen") : null;
        if (aus != null) {
            String gb = attr(aus, "gbtag", "") + ". " + monatName(attr(aus, "gbmonat", "")) + " "
                    + attr(aus, "gbjahr", "") + " BF";
            add(rows, "Geburtstag", gb.trim());
            Element gr = child(rasse, "groesse");
            if (gr != null) {
                add(rows, "Größe", attr(gr, "value", "") + " Halbfinger");
                add(rows, "Gewicht", attr(gr, "gewicht", "") + " Stein");
            }
            add(rows, "Haarfarbe", attr(aus, "haarfarbe", ""));
            add(rows, "Augenfarbe", attr(aus, "augenfarbe", ""));
        }
        return rows;
    }

    private static List<String[]> readBeschreibungRechtsHeld(Element basis, Map<String, String[]> props) {
        List<String[]> rows = new ArrayList<String[]>();
        Element rasse = basis != null ? child(basis, "rasse") : null;
        Element aus = rasse != null ? child(rasse, "aussehen") : null;
        String stand = aus != null ? attr(aus, "stand", "") : "";
        String titel = aus != null ? attr(aus, "titel", "") : "";
        rows.add(new String[]{"Stand", stand});
        rows.add(new String[]{"Titel", titel});
        String so = props.get("sozialstatus") != null ? props.get("sozialstatus")[0] : "";
        rows.add(new String[]{"Sozialstatus", so});
        if (aus != null) {
            StringBuilder fam = new StringBuilder();
            for (int i = 0; i <= 5; i++) {
                String p = attr(aus, "familietext" + i, "");
                if (!p.isEmpty()) {
                    if (fam.length() > 0) fam.append(" ");
                    fam.append(p);
                }
            }
            if (fam.length() > 0) rows.add(new String[]{"Familie / Herkunft", fam.toString()});
        }
        return rows;
    }

    private static List<String[]> readApHeld(Element basis) {
        List<String[]> rows = new ArrayList<String[]>();
        if (basis == null) return rows;
        Element ap = child(basis, "abenteuerpunkte");
        Element fap = child(basis, "freieabenteuerpunkte");
        add(rows, "AP gesamt", attr(ap, "value", ""));
        add(rows, "AP frei", attr(fap, "value", ""));
        return rows;
    }

    private static void readVtHeld(Element vt, List<String> vorteile, List<String> nachteile) {
        if (vt == null) return;
        // einfache Heuristik: bekannte Nachteile-Keywords
        String[] nachKeys = {"Unfähigkeit", "Neugier", "Schlechte", "Weltfremd", "Unstet", "Weltsicht", "Sensibler", "Angst", "Arroganz"};
        NodeList list = vt.getElementsByTagName("vorteil");
        for (int i = 0; i < list.getLength(); i++) {
            Element v = (Element) list.item(i);
            String n = attr(v, "name", "");
            String val = attr(v, "value", "");
            if (!val.isEmpty()) n = n + ": " + val;
            if (n.isEmpty()) continue;
            boolean isN = false;
            for (String k : nachKeys) {
                if (n.contains(k)) { isN = true; break; }
            }
            if (isN) nachteile.add(n); else vorteile.add(n);
        }
    }

    private static List<String> readSfHeld(Element sf) {
        List<String> list = new ArrayList<String>();
        if (sf == null) return list;
        NodeList nodes = sf.getElementsByTagName("sonderfertigkeit");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element s = (Element) nodes.item(i);
            String n = attr(s, "name", "");
            if (!n.isEmpty()) list.add(n);
        }
        return list;
    }

    private static Map<String, List<Talent>> readTalenteHeld(Element tl) {
        Map<String, List<Talent>> map = new LinkedHashMap<String, List<Talent>>();
        if (tl == null) return map;
        // Gruppierung nach grober DSA-Kategorie anhand Name – ohne bereich-Attribut
        String[][] groups = {
            {"Kampf", "Bogen,Dolche,Hiebwaffen,Raufen,Ringen,Säbel,Stäbe,Wurfmesser,Schwerter,Kettenwaffen,Speere"},
            {"Körperlich", "Akrobatik,Athletik,Klettern,Körperbeherrschung,Reiten,Schleichen,Schwimmen,Selbstbeherrschung,Sich verstecken,Singen,Sinnenschärfe,Tanzen,Taschendiebstahl,Zechen,Stimmen imitieren"},
            {"Gesellschaft", "Betören,Gassenwissen,Menschenkenntnis,Sich verkleiden,Überreden,Etikette,Überzeugen"},
            {"Natur", "Fährtensuchen,Fesseln/Entfesseln,Fischen/Angeln,Orientierung,Wildnisleben,Wettervorhersage"},
            {"Wissen", "Geografie,Götter und Kulte,Magiekunde,Pflanzenkunde,Rechnen,Rechtskunde,Sagen und Legenden,Schätzen,Tierkunde,Anatomie,Geschichtswissen,Kriegskunst,Mechanik,Sternkunde"},
            {"Sprachen", "Sprachen kennen"},
            {"Schriften", "Lesen/Schreiben"},
            {"Handwerk", "Ackerbau,Fahrzeug lenken,Heilkunde,Holzbearbeitung,Kochen,Lederarbeiten,Malen/Zeichnen,Musizieren,Schneidern,Alchimie,Bogenbau,Boote fahren"}
        };
        NodeList nodes = tl.getElementsByTagName("talent");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            Talent t = new Talent();
            t.name = attr(el, "name", "");
            t.probe = attr(el, "probe", "");
            t.probeKurz = normalizeProbe(t.probe);
            t.be = attr(el, "be", "");
            try { t.value = Integer.parseInt(attr(el, "value", "0")); } catch (Exception e) { t.value = 0; }
            if (t.name.isEmpty()) continue;
            String bereich = "Sonstige";
            for (String[] g : groups) {
                for (String key : g[1].split(",")) {
                    if (t.name.startsWith(key) || t.name.equals(key)) {
                        bereich = g[0];
                        break;
                    }
                }
                if (!"Sonstige".equals(bereich)) break;
            }
            if (!map.containsKey(bereich)) map.put(bereich, new ArrayList<Talent>());
            map.get(bereich).add(t);
        }
        // AT/PA aus kampf-Element falls vorhanden – parent held
        return map;
    }

    private static List<Zauber> readZauberHeld(Element zl) {
        List<Zauber> list = new ArrayList<Zauber>();
        if (zl == null) return list;
        NodeList nodes = zl.getElementsByTagName("zauber");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element z = (Element) nodes.item(i);
            Zauber zauber = new Zauber();
            zauber.name = attr(z, "name", "");
            zauber.probe = attr(z, "probe", "");
            zauber.probeKurz = normalizeProbe(zauber.probe);
            zauber.variante = attr(z, "variante", "");
            zauber.rep = attr(z, "repraesentation", "");
            zauber.komp = attr(z, "k", "");
            zauber.haus = "true".equalsIgnoreCase(attr(z, "hauszauber", "false"));
            try { zauber.value = Integer.parseInt(attr(z, "value", "0")); } catch (Exception e) { zauber.value = 0; }
            if (!zauber.name.isEmpty()) list.add(zauber);
        }
        return list;
    }


    private static List<Ruestung> readRuestungenHeld(Element held) {
        List<Ruestung> list = new ArrayList<Ruestung>();
        Element ausr = child(held, "ausrüstungen");
        if (ausr == null) ausr = child(held, "ausruestungen");
        if (ausr == null) return list;
        NodeList nodes = ausr.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String slot = attr(e, "name", "");
            if (!slot.startsWith("ruestung")) continue;
            Ruestung r = new Ruestung();
            r.name = firstNonEmpty(attr(e, "ruestungsname", ""), attr(e, "bezeichner", ""));
            r.kopf = ""; r.brust = ""; r.ruecken = ""; r.bauch = "";
            r.linkerarm = ""; r.rechterarm = ""; r.linkesbein = ""; r.rechtesbein = "";
            r.gesamt = ""; r.grs = ""; r.gbe = ""; r.rs = ""; r.be = "";
            if (!r.name.isEmpty()) list.add(r);
        }
        return list;
    }

    private static List<Schild> readSchilderHeld(Element held) {
        List<Schild> list = new ArrayList<Schild>();
        Element ausr = child(held, "ausrüstungen");
        if (ausr == null) ausr = child(held, "ausruestungen");
        if (ausr == null) return list;
        NodeList nodes = ausr.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String slot = attr(e, "name", "");
            String art = attr(e, "verwendungsArt", "");
            if (!slot.startsWith("schild") && !"Schild".equalsIgnoreCase(art)) continue;
            Schild s = new Schild();
            s.name = firstNonEmpty(attr(e, "schildname", ""), attr(e, "bezeichner", ""));
            s.typ = art.isEmpty() ? "Schild" : art;
            s.pa = ""; s.ini = ""; s.mod = ""; s.minbf = ""; s.aktbf = ""; s.bf = "";
            if (!s.name.isEmpty()) list.add(s);
        }
        return list;
    }

    private static List<Waffe> readNahkampfHeld(Element held) {
        List<Waffe> list = new ArrayList<Waffe>();
        Element ausr = child(held, "ausrüstungen");
        Element kampf = child(held, "kampf");
        if (ausr == null) return list;
        NodeList nodes = ausr.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String slot = attr(e, "name", "");
            if (!slot.startsWith("nkwaffe")) continue;
            Waffe w = new Waffe();
            w.name = attr(e, "waffenname", "");
            w.typBe = attr(e, "talent", "");
            w.at = "";
            w.pa = "";
            w.minbf = attr(e, "bfmin", "");
            w.aktbf = attr(e, "bfakt", "");
            String talent = attr(e, "talent", "");
            if (kampf != null && !talent.isEmpty()) {
                NodeList kw = kampf.getElementsByTagName("kampfwerte");
                for (int j = 0; j < kw.getLength(); j++) {
                    Element k = (Element) kw.item(j);
                    if (talent.equals(attr(k, "name", ""))) {
                        Element at = child(k, "attacke");
                        Element pa = child(k, "parade");
                        w.at = attr(at, "value", "");
                        w.pa = attr(pa, "value", "");
                    }
                }
            }
            if (!w.name.isEmpty()) list.add(w);
        }
        return list;
    }

    private static List<Item> readItemsHeld(Element g) {
        List<Item> list = new ArrayList<Item>();
        if (g == null) return list;
        NodeList nodes = g.getElementsByTagName("gegenstand");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            Item item = new Item();
            item.name = attr(el, "name", "");
            item.anzahl = attr(el, "anzahl", "1");
            item.gewicht = "";
            if (!item.name.isEmpty()) list.add(item);
        }
        return list;
    }

    private static String monatName(String m) {
        String[] names = {"", "Praios", "Rondra", "Efferd", "Travia", "Boron", "Hesinde",
                "Firun", "Tsa", "Phex", "Peraine", "Ingerimm", "Rahja"};
        try {
            int i = Integer.parseInt(m);
            if (i >= 1 && i <= 12) return names[i];
        } catch (Exception e) {}
        return m;
    }


    static final class Talent {
        String name, probe, probeKurz, be, at, pa, komp;
        int value;
    }
    static final class Zauber {
        String name, probe, probeKurz, variante, rep, merkmale, komp, lern;
        int value;
        boolean haus;
    }
    static final class Waffe {
        String name, typBe, dk, tp, tpkk, ini, wm, at, pa, efftp, minbf, aktbf;
    }
    static final class Item {
        String name, anzahl, gewicht;
    }
    static final class Ruestung {
        String name, rs, be, kopf, brust, ruecken, bauch;
        String linkerarm, rechterarm, linkesbein, rechtesbein, gesamt;
        String grs, gbe;
    }
    static final class Schild {
        String name, typ, pa, ini, mod, minbf, aktbf, bf;
    }
}
