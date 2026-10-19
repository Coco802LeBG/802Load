package com.coco802.client.gui;

import com.coco802.client.Client802;
import com.coco802.client.module.BoolSetting;
import com.coco802.client.module.Module802;
import com.coco802.client.module.NumberSetting;
import com.coco802.client.module.Setting;
import com.deathmotion.marlowcrystal.MarlowCrystal;
import com.deathmotion.marlowcrystal.config.Settings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/** Menu 802 Client : écran d'accueil à tuiles (PvP, HUD, Visuals, Player, Misc...) puis pages. */
public final class MenuScreen extends Screen {
    private static final int ACCENT = 0xFF2E9BFF;
    private static final String[] NAMES = {"PvP", "HUD", "Visuals", "Player", "Misc", "Wardrobe", "Search", "Social", "Profiles"};
    private static final String[] CATEGORY = {"PvP", "HUD", "Visuals", "Player", "Misc"};
    private static final int[] SCALES = {0, 1, 2, 3, 4, 5, 6};

    private static final String[][] ICONS = {
        {".#######.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", "..#...#..", "..#...#..", "...#.#...", "....#...."},
        {"#########", "#.......#", "#########", "#.......#", "#.#####.#", "#.......#", "#.#####.#", "#.......#", "#########"},
        {"....#....", "....#....", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", "....#....", "....#...."},
        {"#...#....", ".#...#...", "..#...#..", "...#...#.", "....#...#", "...#...#.", "..#...#..", ".#...#...", "#...#...."},
        {".........", "#########", ".........", ".#######.", ".........", "#########", ".........", ".#######.", "........."},
        {"...###...", "..#...#..", "..#...#..", "...###...", "..#####..", ".#.###.#.", ".#..#..#.", "...#.#...", "..##.##.."},
        {"..####...", ".#....#..", "#......#.", "#......#.", "#......#.", ".#....#..", "..####.#.", ".......##", "........#"},
        {".##...##.", "#..#.#..#", "#..#.#..#", ".##...##.", ".........", "##.....##", "#.#...#.#", "#..###..#", "#.......#"},
        {"....#....", "..##.##..", ".#.....#.", "..##.##..", "....#....", ".#.....#.", "#..#.#..#", ".#.....#.", "..#####.."}
    };

    private record Hit(int x1, int y1, int x2, int y2, DoubleConsumer press, DoubleConsumer drag) {
        boolean contains(int x, int y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }

    private static String expanded = null;

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final List<Hit> hits = new ArrayList<>();
    private int view = -1;
    private Hit dragging;
    private boolean wasDown;
    private float alpha = 1f;

    public MenuScreen(Screen parent) {
        super(Text.literal("802 Client"));
        this.parent = parent;
    }

    // ---------- primitives ----------
    private static int col(int argb, float a) {
        int al = (int) (((argb >>> 24) & 0xFF) * a);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private void rect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1, y1, x2, y2, col(color, alpha));
    }

    /** Rectangle aux coins arrondis (2 px). */
    private void rrect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        rect(c, x1 + 2, y1, x2 - 2, y2, color);
        rect(c, x1, y1 + 2, x2, y2 - 2, color);
        rect(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
    }

    private void glass(DrawContext c, int x1, int y1, int x2, int y2, int border, int fill) {
        rrect(c, x1, y1, x2, y2, border);
        rrect(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawTextWithShadow(this.textRenderer, s, x, y, col(color, alpha));
    }

    private void textCentered(DrawContext c, String s, int cx, int y, int color) {
        text(c, s, cx - this.textRenderer.getWidth(s) / 2, y, color);
    }

    private void icon(DrawContext c, int idx, int x, int y, int s, int color) {
        String[] bmp = ICONS[idx];
        for (int j = 0; j < bmp.length; j++)
            for (int i = 0; i < bmp[j].length(); i++)
                if (bmp[j].charAt(i) == '#') rect(c, x + i * s, y + j * s, x + (i + 1) * s, y + (j + 1) * s, color);
    }

    private void sw(DrawContext c, int x, int y, boolean on) {
        rect(c, x, y, x + 22, y + 10, on ? ACCENT : 0xFF3A4452);
        int kx = on ? x + 13 : x + 1;
        rect(c, kx, y + 1, kx + 8, y + 9, 0xFFFFFFFF);
    }

    // ---------- fond translucide : on voit le jeu derrière ----------
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        super.render(ctx, mx, my, delta);
        alpha = Math.max(0.05f, Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f));
        if (view < 0) home(ctx, mx, my); else page(ctx, mx, my);
        handleMouse(mx, my);
    }

    // ---------- accueil ----------
    private void home(DrawContext c, int mx, int my) {
        int bw = 66, gap = 10;
        int total = 5 * bw + 4 * gap;
        int sx = (this.width - total) / 2;
        int ty = this.height / 2 - 60;

        Text title = Text.literal("802 CLIENT").formatted(Formatting.BOLD);
        int tw = this.textRenderer.getWidth(title);
        c.drawTextWithShadow(this.textRenderer, title, (this.width - tw) / 2, ty - 30, col(0xFFFFFFFF, alpha));
        rect(c, (this.width - 40) / 2, ty - 17, (this.width + 40) / 2, ty - 16, ACCENT);

        for (int i = 0; i < 5; i++) tile(c, i, sx + i * (bw + gap), ty, bw, bw, 3, mx, my);

        int sw = 54, sh = 44;
        int total2 = 4 * sw + 3 * gap;
        int sx2 = (this.width - total2) / 2;
        for (int i = 0; i < 4; i++) tile(c, 5 + i, sx2 + i * (sw + gap), ty + bw + 16, sw, sh, 2, mx, my);
    }

    private void tile(DrawContext c, int idx, int x, int y, int w, int h, int scale, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        glass(c, x, y, x + w, y + h, hover ? 0xCCFFFFFF : 0x77FFFFFF, hover ? 0xCC1A2636 : 0xAA0E1620);
        int iconSize = 9 * scale;
        int iy = scale == 3 ? y + 9 : y + 6;
        icon(c, idx, x + (w - iconSize) / 2, iy, scale, hover ? ACCENT : 0xFFFFFFFF);
        textCentered(c, NAMES[idx], x + w / 2, y + h - (scale == 3 ? 14 : 12), hover ? 0xFFFFFFFF : 0xFFC9D3E0);
        hits.add(new Hit(x, y, x + w, y + h, v -> view = idx, null));
    }

    // ---------- pages ----------
    private void page(DrawContext c, int mx, int my) {
        int pw = 400;
        int ph = Math.min(300, this.height - 40);
        int x0 = (this.width - pw) / 2;
        int y0 = (this.height - ph) / 2;
        glass(c, x0, y0, x0 + pw, y0 + ph, 0x77FFFFFF, 0xDD0B1118);

        icon(c, view, x0 + 14, y0 + 10, 2, ACCENT);
        text(c, NAMES[view], x0 + 38, y0 + 14, 0xFFFFFFFF);
        String back = "< Retour";
        int bwid = this.textRenderer.getWidth(back);
        boolean hb = mx >= x0 + pw - bwid - 22 && mx < x0 + pw - 8 && my >= y0 + 8 && my < y0 + 26;
        text(c, back, x0 + pw - bwid - 14, y0 + 14, hb ? 0xFFFFFFFF : 0xFF8FA3BC);
        hits.add(new Hit(x0 + pw - bwid - 22, y0 + 8, x0 + pw - 8, y0 + 26, v -> view = -1, null));
        rect(c, x0 + 14, y0 + 30, x0 + pw - 14, y0 + 31, 0x33FFFFFF);

        int x1 = x0 + 14;
        int x2 = x0 + pw - 14;
        int y = y0 + 40;

        if (view == 0) {
            Settings s = MarlowCrystal.get().settings();
            text(c, "Prediction : Crystal Reflex", x1, y, ACCENT);
            y += 14;
            y = switchRow(c, "Keep Render", x1, x2, y, s.isKeepRender(), () -> s.setKeepRender(!s.isKeepRender()));
            y = switchRow(c, "Raw mouse input", x1, x2, y, s.isRawMouseInput(), () -> s.setRawMouseInput(!s.isRawMouseInput()));
            y = slider(c, "Délai libération (ms)", x1, x2, y, 250, 3000, 50, s.getKeepReleaseMs(), v -> s.setKeepReleaseMs((int) v));
            y = slider(c, "Crystals gardés max", x1, x2, y, 1, 16, 1, s.getMaxKeptCrystals(), v -> s.setMaxKeptCrystals((int) v));
            text(c, "F8 : activer/couper Reflex   F7 : resync du slot tenu", x1, y + 4, 0xFF6F7F94);
            y = modules(c, CATEGORY[0], x1, x2, y + 20, mx, my);
        } else if (view == 1) {
            y = actionRow(c, "Éditeur de HUD (déplace les éléments)", x1, x2, y, () -> this.client.setScreen(new HudEditorScreen(this)));
            modules(c, CATEGORY[1], x1, x2, y, mx, my);
        } else if (view == 2) {
            text(c, "GUI scale par écran", x1, y, ACCENT);
            y += 14;
            for (int i = 0; i < GuiScaleManager.KINDS.length; i++) {
                final String kind = GuiScaleManager.KINDS[i];
                int cur = Client802.CONFIG.scaleFor(kind);
                y = cycleRow(c, GuiScaleManager.LABELS[i], x1, x2, y, cur == 0 ? "Auto" : String.valueOf(cur),
                    () -> Client802.CONFIG.guiScale.put(kind, step(Client802.CONFIG.scaleFor(kind), -1)),
                    () -> Client802.CONFIG.guiScale.put(kind, step(Client802.CONFIG.scaleFor(kind), 1)));
            }
            text(c, "Auto = le GUI scale de tes options Minecraft.", x1, y + 4, 0xFF6F7F94);
            modules(c, CATEGORY[2], x1, x2, y + 20, mx, my);
        } else if (view == 3) {
            modules(c, CATEGORY[3], x1, x2, y, mx, my);
        } else if (view == 4) {
            String[] lines = {
                "802 Client 1.0 - Minecraft 1.21.11 (Fabric)",
                "Menu : Shift droit, ou bouton 802 Client (menu pause)",
                "Config : .minecraft/config/802client.json",
                "Crystal Reflex : .minecraft/config/marlowcrystal.json",
                "Crédits : Marlow / Bram, Coco802 (MIT)"
            };
            for (String l : lines) {
                text(c, l, x1, y, 0xFFB0BAC8);
                y += 14;
            }
            modules(c, CATEGORY[4], x1, x2, y + 10, mx, my);
        } else {
            text(c, "Bientôt.", x1, y, 0xFFB0BAC8);
        }
    }

    private int modules(DrawContext c, String category, int x1, int x2, int y, int mx, int my) {
        for (Module802 m : Client802.MODULES) {
            if (!m.category().equals(category)) continue;
            y = moduleRow(c, m, x1, x2, y, mx, my);
            if (m.name.equals(expanded)) {
                for (Setting s : m.settings) y = settingRow(c, s, x1, x2, y);
            }
        }
        return y;
    }

    private static int step(int cur, int dir) {
        int idx = 0;
        for (int i = 0; i < SCALES.length; i++) if (SCALES[i] == cur) idx = i;
        return SCALES[(idx + dir + SCALES.length) % SCALES.length];
    }

    private int moduleRow(DrawContext c, Module802 m, int x1, int x2, int y, int mx, int my) {
        boolean hover = mx >= x1 && mx < x2 && my >= y && my < y + 26;
        rect(c, x1, y, x2, y + 26, hover ? 0xCC1C2735 : 0xAA151D28);
        rect(c, x1, y, x1 + 2, y + 26, m.isEnabled() ? ACCENT : 0xFF2A3442);
        text(c, m.name, x1 + 10, y + 4, m.isEnabled() ? 0xFFFFFFFF : 0xFFB0BAC8);
        text(c, m.description, x1 + 10, y + 15, 0xFF6F7F94);
        sw(c, x2 - 30, y + 8, m.isEnabled());
        if (!m.settings.isEmpty()) {
            final boolean open = m.name.equals(expanded);
            text(c, open ? "[-]" : "[+]", x2 - 62, y + 9, 0xFF8FA3BC);
            hits.add(new Hit(x2 - 68, y, x2 - 34, y + 26, v -> expanded = open ? null : m.name, null));
        }
        hits.add(new Hit(x1, y, x2, y + 26, v -> { m.toggle(); Client802.saveModules(); }, null));
        return y + 28;
    }

    private int settingRow(DrawContext c, Setting s, int x1, int x2, int y) {
        int ix = x1 + 14;
        if (s instanceof BoolSetting b) {
            return switchRow(c, b.label, ix, x2, y, b.get(), () -> b.set(!b.get()));
        } else if (s instanceof NumberSetting n) {
            return slider(c, n.label, ix, x2, y, n.min, n.max, n.step, n.get(), n::set);
        }
        return y;
    }

    private int actionRow(DrawContext c, String label, int x1, int x2, int y, Runnable action) {
        rect(c, x1, y, x2, y + 22, 0xAA16314F);
        rect(c, x1, y, x1 + 2, y + 22, ACCENT);
        text(c, label, x1 + 10, y + 7, 0xFFFFFFFF);
        text(c, ">", x2 - 14, y + 7, ACCENT);
        hits.add(new Hit(x1, y, x2, y + 22, v -> action.run(), null));
        return y + 26;
    }

    private int switchRow(DrawContext c, String label, int x1, int x2, int y, boolean value, Runnable toggle) {
        rect(c, x1, y, x2, y + 20, 0xAA121922);
        text(c, label, x1 + 8, y + 6, 0xFFD5DCE6);
        sw(c, x2 - 30, y + 5, value);
        hits.add(new Hit(x1, y, x2, y + 20, v -> toggle.run(), null));
        return y + 22;
    }

    private int cycleRow(DrawContext c, String label, int x1, int x2, int y, String value, Runnable dec, Runnable inc) {
        rect(c, x1, y, x2, y + 22, 0xAA121922);
        text(c, label, x1 + 8, y + 7, 0xFFD5DCE6);
        text(c, "<", x2 - 124, y + 7, ACCENT);
        int vw = this.textRenderer.getWidth(value);
        text(c, value, x2 - 70 - vw / 2, y + 7, 0xFFFFFFFF);
        text(c, ">", x2 - 14, y + 7, ACCENT);
        final int split = x2 - 70;
        hits.add(new Hit(x2 - 130, y, x2, y + 22, v -> { if (v < split) dec.run(); else inc.run(); }, null));
        return y + 24;
    }

    private int slider(DrawContext c, String label, int x1, int x2, int y, double min, double max, double step,
                       double value, DoubleConsumer setter) {
        rect(c, x1, y, x2, y + 20, 0xAA121922);
        text(c, label, x1 + 8, y + 6, 0xFFD5DCE6);
        final int bx1 = x2 - 130;
        final int bx2 = x2 - 50;
        double t = Math.max(0, Math.min(1, (value - min) / (max - min)));
        int fx = bx1 + (int) ((bx2 - bx1) * t);
        rect(c, bx1, y + 8, bx2, y + 12, 0xFF2A3442);
        rect(c, bx1, y + 8, fx, y + 12, ACCENT);
        rect(c, fx - 2, y + 5, fx + 2, y + 15, 0xFFFFFFFF);
        String vs = step >= 1 ? String.valueOf((int) Math.round(value)) : String.format("%.2f", value);
        text(c, vs, bx2 + 8, y + 6, 0xFFFFFFFF);
        DoubleConsumer set = mxv -> {
            double tt = Math.max(0, Math.min(1, (mxv - bx1) / (double) (bx2 - bx1)));
            double v = min + tt * (max - min);
            v = Math.round(v / step) * step;
            setter.accept(Math.max(min, Math.min(max, v)));
        };
        hits.add(new Hit(bx1 - 4, y, bx2 + 4, y + 20, set, set));
        return y + 22;
    }

    /** Souris lue directement (GLFW) : indépendant des changements de signatures de Screen. */
    private void handleMouse(int mx, int my) {
        long handle = this.client.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown) {
            for (Hit h : hits) {
                if (h.contains(mx, my)) {
                    h.press().accept(mx);
                    dragging = h;
                    break;
                }
            }
        } else if (down && dragging != null && dragging.drag() != null) {
            dragging.drag().accept(mx);
        }
        if (!down && wasDown) {
            dragging = null;
            Client802.CONFIG.save();
            if (view == 0) MarlowCrystal.get().saveSettings();
        }
        wasDown = down;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
