package helden.plugin;

import javax.swing.ImageIcon;
import javax.swing.JFrame;

/**
 * Basis-Interface aller Helden-Software-Plugins.
 * (Kopie der offiziellen Schnittstelle – beim Kompilieren
 * ggf. durch die Version aus der Helden-Installation ersetzen.)
 */
public interface HeldenPlugin {
    String SIMPLE = "simple execute";

    String getMenuName();
    String getToolTipText();
    ImageIcon getIcon();
    void doWork(JFrame frame);
    String getType();
}
