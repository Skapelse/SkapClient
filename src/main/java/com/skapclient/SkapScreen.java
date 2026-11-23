package com.skapclient;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class SkapScreen extends Screen {

    // ---------- helpers ----------
    private static final class Anim {
        float v, t;
        Anim(float s) { v = t = s; }
        void tick(float dt, float speed) { v += (t - v) * (1f - (float) Math.exp(-speed * dt)); }
    }

    private record Entry(String id, String name, String version, String desc, String authors, String license, Object cfg) { }

    private static int alpha(int c, float a) {
        a = Math.max(0f, Math.min(1f, a));
        int al = (int) (((c >>> 24) & 255) * a);
        return (al << 24) | (c & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int x = (a >>> s) & 255, y = (b >>> s) & 255;
            r |= ((int) (x + (y - x) * t) & 255) << s;
        }
        return r;
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1f - (float) Math.pow(1f - t, 3); }

    // ---------- state ----------
    private static final int ROW_H = 30;
    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private Anim[] rowHover;
    private final Map<String, Anim> hovers = new HashMap<>();
    private final Anim open = new Anim(0), detail = new Anim(1), selY = new Anim(0);
    private final int[] pal = new int[7];
    private float scrollCur, scrollTarget;
    private int selected = 0;
    private boolean closing;
    private long last = System.nanoTime();

    // layout
    private int px, py, pw, ph, listX, listY, listW, listH, detX, detW, btnX, btnY, btnW = 130, btnH = 18;

    public SkapScreen(Screen parent) {
        super(Text.literal("SkapClient"));
        this.parent = parent;
        System.arraycopy(Config.theme.colors, 0, pal, 0, 7);
        loadEntries();
        rowHover = new Anim[entries.size()];
        for (int i = 0; i < rowHover.length; i++) rowHover[i] = new Anim(0);
        open.t = 1;
    }

    private void loadEntries() {
        // Pick up Mod Menu API config screens (if Mod Menu is installed) via reflection, so no hard dependency.
        Map<String, Object> cfgs = new HashMap<>();
        try {
            for (var c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
                try {
                    Object api = c.getEntrypoint();
                    Object f = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
                    if (f != null) cfgs.put(c.getProvider().getMetadata().getId(), f);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            if (m.getContainingMod().isPresent()) continue; // hide nested library jars
            ModMetadata md = m.getMetadata();
            String id = md.getId();
            if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
            String authors = md.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
            String lic = String.join(", ", md.getLicense());
            entries.add(new Entry(id, md.getName(), md.getVersion().getFriendlyString(),
                    md.getDescription() == null || md.getDescription().isBlank() ? "No description provided." : md.getDescription(),
                    authors.isEmpty() ? "Unknown" : authors, lic.isEmpty() ? "Unspecified" : lic, cfgs.get(id)));
        }
        entries.sort(Comparator.comparing(e -> e.name().toLowerCase()));
    }

    // ---------- layout ----------
    private void layout(float a) {
        pw = Math.min(width - 30, 620);
        ph = Math.min(height - 30, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - a) * 18); // slide-in
        listX = px + 8; listY = py + 34; listW = 190; listH = ph - 42;
        detX = listX + listW + 10; detW = px + pw - 8 - detX;
        btnX = detX + 8; btnY = listY + listH - btnH - 8;
    }

    private float maxScroll() { return Math.max(0, entries.size() * ROW_H - listH); }

    private Anim hv(String k) { return hovers.computeIfAbsent(k, x -> new Anim(0)); }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void text(DrawContext c, String s, int x, int y, int col) {
        if ((col >>> 24) < 8) return;
        c.drawText(textRenderer, s, x, y, col, false);
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext ctx, int mx, int my, float tickDelta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1_000_000_000f);
        last = now;

        // theme colour cross-fade
        int[] target = Config.theme.colors;
        for (int i = 0; i < 7; i++) pal[i] = lerp(pal[i], target[i], 1f - (float) Math.exp(-8 * dt));

        open.tick(dt, 12);
        detail.tick(dt, 14);
        selY.t = selected * ROW_H;
        selY.tick(dt, 16);
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-14 * dt));

        if (closing && open.v < 0.03f) { client.setScreen(parent); return; }

        float a = ease(open.v);
        layout(a);
        int BG = alpha(pal[Theme.BG], a), PANEL = alpha(pal[Theme.PANEL], a), ROW = pal[Theme.ROW];
        int ACC = pal[Theme.ACC], ACC2 = pal[Theme.ACC2], TXT = pal[Theme.TEXT], SUB = pal[Theme.SUB];

        ctx.fill(0, 0, width, height, alpha(0xFF000000, a * 0.55f));

        // window + glowing border
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, alpha(ACC, a * 0.55f));
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.fillGradient(px, py, px + pw, py + 28, alpha(ACC, a * 0.28f), alpha(ACC, 0));
        text(ctx, "SKAPCLIENT", px + 10, py + 10, alpha(ACC2, a));
        text(ctx, entries.size() + " mods loaded", px + 10 + textRenderer.getWidth("SKAPCLIENT") + 8, py + 10, alpha(SUB, a));

        // theme swatches
        Theme[] themes = Theme.values();
        for (int i = 0; i < themes.length; i++) {
            int sx = px + pw - 12 - (themes.length - i) * 20, sy = py + 8;
            Anim h = hv("sw" + i);
            h.t = in(mx, my, sx - 2, sy - 2, 16, 16) ? 1 : 0; h.tick(dt, 18);
            int grow = Math.round(h.v * 2);
            boolean active = themes[i] == Config.theme;
            if (active) ctx.fill(sx - 3 - grow, sy - 3 - grow, sx + 13 + grow, sy + 13 + grow, alpha(TXT, a * 0.9f));
            ctx.fill(sx - grow, sy - grow, sx + 10 + grow, sy + 10 + grow, alpha(themes[i].colors[Theme.ACC], a));
            if (h.v > 0.5f) {
                String l = themes[i].label;
                text(ctx, l, px + pw - 12 - themes.length * 20 - textRenderer.getWidth(l) - 6, py + 10, alpha(SUB, a * h.v));
            }
        }

        // list
        ctx.fill(listX, listY, listX + listW, listY + listH, PANEL);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        // animated selection highlight
        int hy = listY + Math.round(selY.v - scrollCur);
        ctx.fill(listX, hy, listX + listW, hy + ROW_H, alpha(ACC, a * 0.22f));
        ctx.fill(listX, hy, listX + 3, hy + ROW_H, alpha(ACC, a));

        for (int i = 0; i < entries.size(); i++) {
            int ry = listY + i * ROW_H - Math.round(scrollCur);
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            Anim h = rowHover[i];
            h.t = in(mx, my, listX, listY, listW, listH) && in(mx, my, listX, ry, listW, ROW_H) ? 1 : 0;
            h.tick(dt, 18);
            ctx.fill(listX, ry, listX + listW, ry + ROW_H, alpha(ROW, a * h.v * 0.8f));
            int shift = Math.round(h.v * 3);
            Entry e = entries.get(i);
            int avCol = lerp(ROW, ACC, 0.5f + 0.5f * h.v);
            ctx.fill(listX + 8 + shift, ry + 5, listX + 28 + shift, ry + 25, alpha(avCol, a));
            String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
            text(ctx, letter, listX + 18 + shift - textRenderer.getWidth(letter) / 2, ry + 11, alpha(TXT, a));
            text(ctx, textRenderer.trimToWidth(e.name(), listW - 50), listX + 34 + shift, ry + 6, alpha(TXT, a));
            text(ctx, textRenderer.trimToWidth("v" + e.version(), listW - 50), listX + 34 + shift, ry + 17, alpha(SUB, a));
        }
        ctx.disableScissor();

        // scrollbar
        if (maxScroll() > 0) {
            float frac = listH / (float) (entries.size() * ROW_H);
            int bh = Math.max(14, (int) (listH * frac));
            int by = listY + (int) ((listH - bh) * (scrollCur / maxScroll()));
            ctx.fill(listX + listW - 3, by, listX + listW - 1, by + bh, alpha(ACC, a * 0.7f));
        }

        // detail panel
        ctx.fill(detX, listY, detX + detW, listY + listH, PANEL);
        if (!entries.isEmpty()) {
            Entry e = entries.get(selected);
            float d = ease(detail.v);
            float da = a * d;
            int ox = Math.round((1f - d) * 10);
            int dx = detX + 10 + ox, dy = listY + 10;

            ctx.fill(dx, dy, dx + 34, dy + 34, alpha(ACC, da));
            String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
            text(ctx, letter, dx + 17 - textRenderer.getWidth(letter) / 2, dy + 13, alpha(TXT, da));
            text(ctx, textRenderer.trimToWidth(e.name(), detW - 70), dx + 44, dy + 3, alpha(TXT, da));
            text(ctx, textRenderer.trimToWidth(e.id() + "  ·  v" + e.version(), detW - 70), dx + 44, dy + 16, alpha(ACC2, da));
            text(ctx, textRenderer.trimToWidth("Authors: " + e.authors(), detW - 24), dx, dy + 44, alpha(SUB, da));
            text(ctx, textRenderer.trimToWidth("License: " + e.license(), detW - 24), dx, dy + 56, alpha(SUB, da));
            ctx.fill(dx, dy + 69, dx + detW - 20, dy + 70, alpha(ACC, da * 0.5f));

            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(e.desc()), detW - 24);
            int ly = dy + 77, maxY = btnY - 10;
            for (OrderedText l : lines) {
                if (ly + 9 > maxY) break;
                if (da * 255 >= 8) ctx.drawText(textRenderer, l, dx, ly, alpha(TXT, da), false);
                ly += 11;
            }

            // settings button
            boolean has = e.cfg() != null;
            Anim bh = hv("btn");
            bh.t = has && in(mx, my, btnX, btnY, btnW, btnH) ? 1 : 0; bh.tick(dt, 18);
            int bcol = has ? lerp(alpha(ACC, 0.65f), ACC2, bh.v) : alpha(ROW, 1f);
            int grow = Math.round(bh.v * 2);
            ctx.fill(btnX - grow, btnY - grow, btnX + btnW + grow, btnY + btnH + grow, alpha(bcol, da));
            String label = has ? "Open Settings" : "No settings available";
            text(ctx, label, btnX + btnW / 2 - textRenderer.getWidth(label) / 2, btnY + 5, alpha(has ? TXT : SUB, da));
        }
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        Theme[] themes = Theme.values();
        for (int i = 0; i < themes.length; i++) {
            int sx = px + pw - 12 - (themes.length - i) * 20, sy = py + 8;
            if (in(mx, my, sx - 2, sy - 2, 16, 16)) {
                Config.theme = themes[i];
                Config.save();
                return true;
            }
        }
        if (in(mx, my, listX, listY, listW, listH)) {
            int idx = (int) ((my - listY + scrollCur) / ROW_H);
            if (idx >= 0 && idx < entries.size() && idx != selected) {
                selected = idx;
                detail.v = 0; detail.t = 1; // replay detail slide-in
            }
            return true;
        }
        if (!entries.isEmpty() && in(mx, my, btnX, btnY, btnW, btnH)) {
            Object f = entries.get(selected).cfg();
            if (f != null) {
                try {
                    Class<?> iface = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
                    Object screen = iface.getMethod("create", Screen.class).invoke(f, this);
                    client.setScreen((Screen) screen);
                } catch (Throwable t) { t.printStackTrace(); }
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 34f;
        return true;
    }

    @Override
    public void close() {
        if (!closing) { closing = true; open.t = 0; }
    }

    @Override
    public boolean shouldPause() { return false; }
}
