package dev.oac.client;

import dev.oac.core.OacMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native, client-side calculator UI. All user-facing strings come from Minecraft translations. */
public final class OacScreen extends Screen {
    private static final int OUTER = 0xFF0B1019;
    private static final int CARD = 0xFF172334;
    private static final int CARD_DARK = 0xFF111B2B;
    private static final int CARD_ALT = 0xFF1A2A3D;
    private static final int LINE = 0xFF30465C;
    private static final int ACCENT = 0xFF81C8FF;
    private static final int ACCENT_SOFT = 0xFF325F80;
    private static final int WHITE = 0xFFEAF5FF;
    private static final int MUTED = 0xFF9BB0C6;
    private static final int DANGER = 0xFFFFA2A2;
    private static final String[] GROUP_KEYS = {"main", "fine", "vector", "debug"};
    private static final String[][] GROUP_FIELDS = {
        {"mainCountX", "mainCountZ", "xVArrow", "zVArrow", "xVBaseTnT", "zVBaseTnT"},
        {"fineXCount", "fineZCount", "xVArrowAddCount", "zVArrowAddCount", "bounceCount"},
        {"sideTnTDirection", "vectorCorrectionDirection", "vectorCorrectionCoarseCount", "vectorCorrectionFineCount", "vectorCorrectionMotion", "directionFlip", "fuseFlip"},
        {"relativeDirection", "fineX", "fineZ", "xVArrowAdd", "zVArrowAdd", "xVArrowReduced", "zVArrowReduced", "xVArrowAddReduced", "zVArrowAddReduced", "sideGapX", "sideGapZ"}
    };
    private static final String[] INPUT_KEYS = {"cannon_x", "cannon_z", "target_x", "target_z", "diff_x", "diff_z"};

    // Inputs are retained when the user closes and reopens the in-game calculator.
    private static final String[] SAVED_INPUTS = {"", "", "", "", "", ""};
    private static boolean savedDebug;

    private record ResultLine(String key, boolean heading, int category) {}

    private final Screen parent;
    private final TextFieldWidget[] fields = new TextFieldWidget[6];
    private final List<ResultLine> resultLines = new ArrayList<>();
    private boolean debug = savedDebug;
    private Map<String, Object> results;
    private String messageKey = "oac.message.empty";
    private String messageArgument = null;
    private boolean error;
    private int scroll;

    private int winX, winY, winW, winH;
    private int inputX, inputY, inputW;
    private int outputX, outputY, outputW, outputBottom;
    private int resultsStart;
    private boolean wide;

    public OacScreen(Screen parent) {
        super(Text.literal("OAC Calculator"));
        this.parent = parent;
    }

    private static Text t(String suffix) { return Text.translatable("oac." + suffix); }
    private static String s(String suffix) { return t(suffix).getString(); }

    private void addButton(String key, int x, int y, int width, int height, Runnable action) {
        addDrawableChild(ButtonWidget.builder(t("button." + key), button -> action.run())
            .dimensions(x, y, Math.max(20, width), height).build());
    }

    private void modeButton(String modeKey, boolean newDebug, int x, int y, int width) {
        addDrawableChild(ButtonWidget.builder(t("mode." + modeKey), button -> changeMode(newDebug))
            .dimensions(x, y, Math.max(26, width), 20).build());
    }

    private void addInput(int id, int x, int y, int width) {
        String label = s("input." + INPUT_KEYS[id]);
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, Math.max(34, width), 20, Text.literal(label));
        field.setMaxLength(27);
        field.setPlaceholder(Text.literal(label));
        field.setText(SAVED_INPUTS[id]);
        field.setChangedListener(value -> {
            SAVED_INPUTS[id] = value;
            messageKey = "oac.message.empty";
            messageArgument = null;
            error = false;
            results = null;
            scroll = 0;
            resultLines.clear();
        });
        fields[id] = addDrawableChild(field);
    }

    private void changeMode(boolean newDebug) {
        if (debug == newDebug) return;
        debug = newDebug;
        savedDebug = newDebug;
        error = false;
        messageKey = "oac.message.empty";
        messageArgument = null;
        results = null;
        scroll = 0;
        resultLines.clear();
        clearAndInit();
    }

    @Override
    protected void init() {
        winW = Math.min(900, this.width - 16);
        winH = Math.min(580, this.height - 12);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        wide = winW >= 650;
        inputX = winX + 12;
        inputY = winY + 52;
        int footerY = winY + winH - 29;

        if (wide) {
            inputW = Math.min(275, (winW - 36) * 2 / 5);
            outputX = inputX + inputW + 12;
            outputY = inputY;
            outputW = winX + winW - 12 - outputX;
        } else {
            inputW = winW - 24;
            outputX = inputX;
            outputW = inputW;
            outputY = inputY + (debug ? 141 : 182);
        }
        outputBottom = footerY - 8;
        resultsStart = outputY + 38;
        int innerX = inputX + 10;
        int innerW = inputW - 20;
        int halfW = (innerW - 8) / 2;

        modeButton("normal", false, innerX, inputY + 29, halfW);
        modeButton("debug", true, innerX + halfW + 8, inputY + 29, innerW - halfW - 8);

        if (debug) {
            addInput(4, innerX, inputY + 77, halfW);
            addInput(5, innerX + halfW + 8, inputY + 77, innerW - halfW - 8);
            addButton("calculate", innerX, inputY + 108, innerW, 22, this::calculate);
        } else {
            addInput(0, innerX, inputY + 77, halfW);
            addInput(1, innerX + halfW + 8, inputY + 77, innerW - halfW - 8);
            addInput(2, innerX, inputY + 117, halfW);
            addInput(3, innerX + halfW + 8, inputY + 117, innerW - halfW - 8);
            if (client != null && client.player != null) {
                addButton("chunk", innerX, inputY + 146, halfW, 19, this::setChunk);
                addButton("player", innerX + halfW + 8, inputY + 146, innerW - halfW - 8, 19, this::setTarget);
            }
            addButton("calculate", innerX, inputY + 174, innerW, 22, this::calculate);
        }

        int scrollControlY = outputY + 9;
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), button -> moveScroll(-1))
            .dimensions(outputX + outputW - 58, scrollControlY, 22, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), button -> moveScroll(1))
            .dimensions(outputX + outputW - 33, scrollControlY, 22, 20).build());
        addButton("close", winX + winW - 85, footerY + 2, 73, 21, this::close);
    }

    private void setChunk() {
        if (client == null || client.player == null) {
            fail("no_world"); return;
        }
        int x = Math.floorDiv(client.player.getBlockX(), 16) * 16;
        int z = Math.floorDiv(client.player.getBlockZ(), 16) * 16;
        fields[0].setText(Integer.toString(x));
        fields[1].setText(Integer.toString(z));
    }

    private void setTarget() {
        if (client == null || client.player == null) {
            fail("no_world"); return;
        }
        fields[2].setText(Integer.toString(client.player.getBlockX()));
        fields[3].setText(Integer.toString(client.player.getBlockZ()));
    }

    private double decimal(int id) {
        try {
            double n = Double.parseDouble(SAVED_INPUTS[id].trim().replace(',', '.'));
            if (!Double.isFinite(n)) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("number:" + id);
        }
    }

    private int integer(int id) {
        try {
            return Integer.parseInt(SAVED_INPUTS[id].trim());
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("integer:" + id);
        }
    }

    private void fail(String errorName) {
        results = null;
        resultLines.clear();
        scroll = 0;
        error = true;
        messageArgument = null;
        messageKey = "oac.message.error_" + errorName;
    }

    private void calculate() {
        results = null;
        resultLines.clear();
        scroll = 0;
        try {
            if (debug) {
                results = OacMath.calculate(true, OacMath.NUKE_LAYER, decimal(4), decimal(5));
            } else {
                results = OacMath.calculateNormal(integer(0), integer(1), integer(2), integer(3));
            }
            for (int group = 0; group < (debug ? 4 : 3); group++) {
                resultLines.add(new ResultLine(GROUP_KEYS[group], true, group));
                for (String field : GROUP_FIELDS[group]) {
                    if (results.containsKey(field)) resultLines.add(new ResultLine(field, false, group));
                }
            }
            messageKey = "oac.message.ready";
            messageArgument = null;
            error = false;
        } catch (IllegalArgumentException exception) {
            String msg = exception.getMessage() == null ? "" : exception.getMessage();
            if (msg.startsWith("number:") || msg.startsWith("integer:")) {
                boolean whole = msg.startsWith("integer:");
                try {
                    int id = Integer.parseInt(msg.substring(msg.indexOf(':') + 1));
                    messageKey = whole ? "oac.message.error_integer" : "oac.message.error_number";
                    messageArgument = s("input." + INPUT_KEYS[id]);
                    error = true;
                } catch (NumberFormatException ex) {
                    fail("generic");
                }
            } else if (msg.contains("divisible by 16")) fail("chunk");
            else if (msg.contains("100 blocks")) fail("distance");
            else if (msg.contains("same position")) fail("same");
            else if (msg.contains("cannot be 0")) fail("zero_layer");
            else fail("generic");
        } catch (RuntimeException ex) {
            fail("generic");
        }
    }

    private int visibleRows() { return Math.max(0, (outputBottom - resultsStart - 7) / 18); }
    private int maxScroll() { return Math.max(0, resultLines.size() - visibleRows()); }
    private void moveScroll(int delta) { scroll = Math.max(0, Math.min(maxScroll(), scroll + delta)); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= outputX && mouseX < outputX + outputW && mouseY >= outputY && mouseY < outputBottom) {
            if (verticalAmount != 0) {
                moveScroll(verticalAmount > 0 ? -3 : 3);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static String value(Object object) {
        if (object == null) return "—";
        if (object instanceof String str) {
            if (str.equals("ON")) return s("value.on");
            if (str.equals("OFF")) return s("value.off");
            return str;
        }
        if (!(object instanceof Number number)) return String.valueOf(object);
        DecimalFormat format = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(6);
        return format.format(number.doubleValue());
    }

    private String fit(String input, int maxWidth) {
        if (maxWidth <= 8) return "";
        if (textRenderer.getWidth(input) <= maxWidth) return input;
        String left = input;
        while (left.length() > 1 && textRenderer.getWidth(left + "…") > maxWidth) {
            left = left.substring(0, left.length() - 1);
        }
        return left + "…";
    }

    private void draw(DrawContext ctx, String str, int x, int y, int color, int maxWidth) {
        ctx.drawTextWithShadow(textRenderer, fit(str, maxWidth), x, y, color);
    }

    private void paintFrame(DrawContext ctx, int x, int y, int w, int h, int fill) {
        if (w < 2 || h < 2) return;
        ctx.fill(x, y, x + w, y + h, LINE);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Minecraft 1.21.11 can apply the screen background blur before this render call.
        // Calling renderBackground() again causes: "Can only blur once per frame".
        // The translucent overlay below dims the world without requesting another blur.
        context.fill(0, 0, width, height, 0xA0060B12);
        paintFrame(context, winX, winY, winW, winH, OUTER);
        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 43, CARD);
        context.fill(winX + 1, winY + 42, winX + winW - 1, winY + 44, ACCENT_SOFT);
        draw(context, s("title"), winX + 14, winY + 9, WHITE, winW - 26);
        draw(context, s("subtitle"), winX + 14, winY + 26, MUTED, winW - 26);

        int formBottom = wide ? outputBottom : outputY - 8;
        paintFrame(context, inputX, inputY, inputW, Math.max(5, formBottom - inputY), CARD);
        context.fill(inputX + 1, inputY + 1, inputX + inputW - 1, inputY + 21, CARD_DARK);
        draw(context, s("section.inputs"), inputX + 10, inputY + 7, ACCENT, inputW - 18);

        int ix = inputX + 10;
        draw(context, debug ? s("hint.debug") : s("hint.origin"), ix, inputY + 62, MUTED, inputW - 20);
        if (!debug) draw(context, s("hint.target"), ix, inputY + 103, MUTED, inputW - 20);
        if (wide) {
            int hintY = inputY + (debug ? 161 : 226);
            draw(context, s("section.help"), ix, hintY, ACCENT, inputW - 22);
            String tip = s(debug ? "hint.debug_help" : "hint.normal_help");
            // Wrap the long explanation to keep it inside the input card.
            int maxWidth = inputW - 22;
            String[] words = tip.split(" ");
            StringBuilder current = new StringBuilder();
            int lineY = hintY + 17;
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textRenderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
                    draw(context, current.toString(), ix, lineY, MUTED, maxWidth);
                    lineY += 12;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) draw(context, current.toString(), ix, lineY, MUTED, maxWidth);
        }

        paintFrame(context, outputX, outputY, outputW, Math.max(2, outputBottom - outputY), CARD);
        context.fill(outputX + 1, outputY + 1, outputX + outputW - 1, outputY + 32, CARD_DARK);
        draw(context, s("section.results"), outputX + 11, outputY + 10, ACCENT, outputW - 80);
        context.fill(outputX + 10, outputY + 33, outputX + outputW - 10, outputY + 34, LINE);

        if (results == null) {
            draw(context, s("message.empty"), outputX + 12, resultsStart + 10, MUTED, outputW - 24);
        } else {
            scroll = Math.max(0, Math.min(maxScroll(), scroll));
            int n = visibleRows();
            for (int row = 0; row < n && row + scroll < resultLines.size(); row++) {
                ResultLine item = resultLines.get(row + scroll);
                int y = resultsStart + row * 18;
                if (item.heading()) {
                    context.fill(outputX + 7, y - 2, outputX + outputW - 7, y + 15, ACCENT_SOFT);
                    draw(context, s("group." + item.key()), outputX + 13, y + 2, WHITE, outputW - 26);
                } else {
                    if ((row + scroll) % 2 == 0) {
                        context.fill(outputX + 7, y - 2, outputX + outputW - 7, y + 15, CARD_ALT);
                    }
                    int maxLabel = (outputW - 26) * 57 / 100;
                    String label = s("field." + item.key());
                    String number = value(results.get(item.key()));
                    draw(context, label, outputX + 12, y + 2, MUTED, maxLabel);
                    int numberWidth = textRenderer.getWidth(number);
                    int allowed = Math.max(18, outputW - maxLabel - 34);
                    String fitted = fit(number, allowed);
                    context.drawTextWithShadow(textRenderer, fitted,
                        outputX + outputW - 13 - textRenderer.getWidth(fitted), y + 2, WHITE);
                }
            }
            if (resultLines.size() > n && n > 0 && outputW > 140) {
                // Thin visual scroll indicator: total/visible item ratios are both data-driven.
                int railTop = resultsStart;
                int railHeight = Math.max(6, outputBottom - resultsStart - 7);
                context.fill(outputX + outputW - 5, railTop, outputX + outputW - 3, railTop + railHeight, LINE);
                int knobHeight = Math.max(10, railHeight * n / Math.max(1, resultLines.size()));
                int knobTravel = railHeight - knobHeight;
                int knobTop = railTop + (maxScroll() == 0 ? 0 : knobTravel * scroll / maxScroll());
                context.fill(outputX + outputW - 5, knobTop, outputX + outputW - 3, knobTop + knobHeight, ACCENT);
            }
        }

        String msg = messageArgument != null
            ? Text.translatable(messageKey, messageArgument).getString()
            : Text.translatable(messageKey).getString();
        int statusWidth = winW - 107;
        draw(context, msg, winX + 13, winY + winH - 21, error ? DANGER : MUTED, statusWidth);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
