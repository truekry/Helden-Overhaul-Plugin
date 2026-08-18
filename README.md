# Helden-Overhaul – Plugin für die Helden-Software

Dieses Plugin bringt **Helden-Overhaul** direkt in die [Helden-Software](https://www.helden-software.de/) (DSA / Das Schwarze Auge). Statt eines nachträglichen Python-Exports erzeugt es aus dem **aktuellen Held** über die Plugin-API einen modernen, interaktiven HTML-Charakterbogen.

**Ursprung / Vorlage:** Das Layout, die Würfel-Logik und der Funktionsumfang basieren auf dem Projekt  
[Helden-Overhaul](https://github.com/truekry/Helden-Overhaul) (Python-Skript zur Modernisierung exportierter HTML-Bögen).

---

## Features

- Modernes Charakterbogen-Layout mit Navigation und Dark Mode
- Interaktive Würfel: Eigenschaften (MU, KL, IN …), Talente, Zauber, Initiative
- Freie Würfe (1W6 / 1W20) und Sitzungs-Würfellog
- BE-Abzug aus getragener Rüstung bei Talentproben
- Elfen-Repräsentation: bei Zauber mit REP „Elf“ und IN > KL wird einmal KL durch IN ersetzt (nie 3× IN)
- Alles in **einer** HTML-Datei (CSS und JavaScript eingebettet)

---

## Installation

1. `ModernBogenPlugin.jar` in den **Plugin-Ordner** der Helden-Software legen  
   (typisch: `…/helden/plugins` neben `helden.jar`).
2. Helden-Software **neu starten**.
3. Menü: **Helden-Overhaul → HTML exportieren**.

**Wichtig:** Die Helden-Software läuft unter **Java 8**. Das Plugin ist entsprechend mit `-source 1.8 -target 1.8` gebaut.

---

## Nutzung

1. Held in der Helden-Software öffnen.
2. **Erweiterungen → Helden-Overhaul → HTML exportieren**.
3. Speicherort wählen – es entsteht eine eigenständige `.html`-Datei.
4. Datei im Browser öffnen.

---

## Hinweise

- Entwickelt mit Unterstützung generativer KI.
- Viel Spaß beim Würfeln und Abenteuern.
