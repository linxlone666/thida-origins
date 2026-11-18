package dev.thida;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Only the server builds the roster and commits membership. This screen edits a local draft. */
final class ThidaRuenMenu extends Screen {
  private static final int ROW_HEIGHT = 19;
  private static final int LIST_TOP = 72;

  private record Row(UUID id, String name, int distance) {}

  private final UUID activation;
  private CompoundTag latest;
  private String side = "inside", feedback = "";
  private UUID selected;
  private boolean loaded;
  private int ticks, pendingUntil;
  private int left, top, panelWidth, panelHeight, rowCount;
  private RosterButton inside, outside, aim, confirm;
  private RosterColumn candidates, members;

  ThidaRuenMenu(UUID activation) {
    super(label(text("title")));
    this.activation = activation;
    ThidaHudState.acceptRuen(null);
  }

  @Override
  protected void init() {
    panelWidth = Math.min(width - 12, 336);
    rowCount = Math.clamp((height - 128) / ROW_HEIGHT, 1, 6);
    panelHeight = 116 + rowCount * ROW_HEIGHT;
    left = (width - panelWidth) / 2;
    top = Math.max(2, (height - panelHeight) / 2);
    int x = left + 8, usable = panelWidth - 16, half = (usable - 4) / 2;
    inside = button("", x, top + 28, half, 21, Tone.TAB, () -> changeSide("inside"));
    inside.icon = "ruen_inside";
    outside =
        button(
            "",
            x + half + 4,
            top + 28,
            usable - half - 4,
            21,
            Tone.TAB,
            () -> changeSide("outside"));
    outside.icon = "ruen_outside";
    int gap = 8, columnWidth = (usable - gap) / 2;
    candidates = new RosterColumn(x, columnWidth, false);
    members = new RosterColumn(x + columnWidth + gap, usable - columnWidth - gap, true);
    int bottom = top + panelHeight - 24, third = (usable - 8) / 3;
    aim =
        button(
            text("aim_button"),
            x,
            bottom,
            third,
            18,
            Tone.PLAIN,
            () -> {
              onClose();
              ThidaClient.armRuenAim(activation, side);
            });
    confirm =
        button(
            text("confirm"),
            x + third + 4,
            bottom,
            third,
            18,
            Tone.PRIMARY,
            () -> {
              if (selected == null) return;
              pendingUntil = ticks + 40;
              feedback = "waiting";
              ThidaClient.ruenRequest("ruen_list_pick", activation, side, selected);
              refresh();
            });
    button(
        text("cancel"),
        x + 2 * (third + 4),
        bottom,
        usable - 2 * (third + 4),
        18,
        Tone.DANGER,
        () -> {
          ThidaClient.ruenRequest("ruen_cancel", activation, "", null);
          onClose();
        });
    refresh();
    requestRoster();
  }

  private RosterButton button(String name, int x, int y, int w, int h, Tone tone, Runnable action) {
    return addRenderableWidget(new RosterButton(name, x, y, w, h, tone, action));
  }

  private void requestRoster() {
    ThidaClient.ruenRequest("ruen_roster", activation, "", null);
  }

  @Override
  public void tick() {
    var hud = ThidaHudState.snapshot();
    if (minecraft.player == null
        || !minecraft.player.isAlive()
        || minecraft.player.isSpectator()
        || !hud.hasUUID("ruenActivation")
        || !activation.equals(hud.getUUID("ruenActivation"))
        || ThidaHudState.remaining(hud.getLong("domain")) <= 0) {
      onClose();
      return;
    }
    ticks++;
    if (ticks % 20 == 0) requestRoster();
    var update = ThidaHudState.ruenRoster();
    if (update != latest
        && update.hasUUID("activation")
        && activation.equals(update.getUUID("activation"))) {
      latest = update;
      loaded = true;
      String result = ThidaHudState.takeRuenFeedback(activation);
      if (!result.isEmpty()) {
        pendingUntil = 0;
        selected = null;
        feedback = result;
      }
      refresh();
    }
    if (pendingUntil > 0 && ticks >= pendingUntil) {
      pendingUntil = 0;
      feedback = "no_response";
      refresh();
    }
  }

  private void changeSide(String nextSide) {
    if (side.equals(nextSide)) return;
    side = nextSide;
    candidates.offset = 0;
    members.offset = 0;
    selected = null;
    if (pendingUntil == 0) feedback = "";
    refresh();
  }

  private int count(String team) {
    if (latest != null) return latest.getList(team, Tag.TAG_COMPOUND).size();
    return ThidaHudState.snapshot().getInt(team.equals("inside") ? "ruenInside" : "ruenOutside");
  }

  private List<Row> readRows(String key) {
    var updated = new ArrayList<Row>();
    if (latest != null) {
      var list = latest.getList(key, Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        var entry = list.getCompound(i);
        if (entry.hasUUID("id"))
          updated.add(
              new Row(entry.getUUID("id"), entry.getString("name"), entry.getInt("distance")));
      }
    }
    return updated;
  }

  private void refresh() {
    candidates.rows = readRows("candidates");
    members.rows = readRows(side);
    if (selected != null && selectedRow() == null && pendingUntil == 0) {
      selected = null;
      feedback = "no_target";
    }
    inside.marked = side.equals("inside");
    outside.marked = side.equals("outside");
    inside.active = outside.active = pendingUntil == 0;
    inside.setCaption(text("inside"), count("inside") + "/" + ThidaRuenSaneha.CAPACITY);
    outside.setCaption(text("outside"), count("outside") + "/" + ThidaRuenSaneha.CAPACITY);
    boolean canChoose = count(side) < ThidaRuenSaneha.CAPACITY && pendingUntil == 0;
    aim.active = canChoose;
    confirm.active = canChoose && selectedRow() != null;
    candidates.refresh(canChoose);
    members.refresh(false);
  }

  private Row selectedRow() {
    for (var row : candidates.rows) if (row.id().equals(selected)) return row;
    return null;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0 || y < top + 52 || y >= top + panelHeight - 44) return false;
    if (candidates.containsX(x)) return candidates.move(vertical < 0 ? 1 : -1);
    return members.containsX(x) && members.move(vertical < 0 ? 1 : -1);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    minecraft.options.keyAttack.setDown(false);
    while (minecraft.options.keyAttack.consumeClick()) {}
    super.onClose();
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    ThidaHudTheme.popup(g, left, top, panelWidth, panelHeight, 24);
    ThidaGui.icon(g, "domain", left + 8, top + 4, 1);
    ThidaGui.text(g, text("title"), left + 28, top + 9, panelWidth - 76, ThidaHudTheme.ON_DARK);
    String time =
        ThidaHudState.time(ThidaHudState.remaining(ThidaHudState.snapshot().getLong("domain")));
    ThidaGui.fittedText(g, time, left + panelWidth - 43, top + 9, 35, ThidaHudTheme.SELECTION);
    candidates.render(g);
    members.render(g);
    g.fill(members.x - 4, top + 53, members.x - 3, top + panelHeight - 48, 0xFFBC926C);
    Row row = selectedRow();
    String status =
        pendingUntil != 0 || !feedback.isEmpty()
            ? text(feedback)
            : count(side) >= ThidaRuenSaneha.CAPACITY
                ? text("full")
                : row == null ? "" : text("confirm_to") + " " + text(side) + ": " + row.name();
    ThidaHudTheme.rule(g, left + 8, top + panelHeight - 44, panelWidth - 16);
    ThidaGui.text(
        g,
        status,
        left + 8,
        top + panelHeight - 40,
        panelWidth - 16,
        row != null || !feedback.isEmpty() ? ThidaHudTheme.ACTIVE : ThidaHudTheme.SECONDARY);
    super.render(g, mouseX, mouseY, partial);
  }

  private final class RosterColumn {
    private final int x, width;
    private final boolean locked;
    private final List<RosterButton> buttons = new ArrayList<>();
    private final RosterButton previous, next;
    private List<Row> rows = List.of();
    private int offset;

    RosterColumn(int x, int width, boolean locked) {
      this.x = x;
      this.width = width;
      this.locked = locked;
      previous = button("‹", x + width - 33, top + 53, 15, 15, Tone.PLAIN, () -> move(-rowCount));
      next = button("›", x + width - 15, top + 53, 15, 15, Tone.PLAIN, () -> move(rowCount));
      for (int i = 0; i < rowCount; i++) {
        final int slot = i;
        var entry =
            button(
                "",
                x,
                top + LIST_TOP + i * ROW_HEIGHT,
                width,
                17,
                Tone.ROW,
                () -> {
                  if (locked || offset + slot >= rows.size()) return;
                  selected = rows.get(offset + slot).id();
                  feedback = "";
                  ThidaRuenMenu.this.refresh();
                });
        entry.readOnly = locked;
        buttons.add(entry);
      }
    }

    boolean containsX(double mouseX) {
      return mouseX >= x && mouseX < x + width;
    }

    boolean move(int delta) {
      int nextOffset = Math.clamp(offset + delta, 0, Math.max(0, rows.size() - rowCount));
      if (nextOffset == offset) return false;
      offset = nextOffset;
      ThidaRuenMenu.this.refresh();
      return true;
    }

    void refresh(boolean canChoose) {
      offset = Math.min(offset, Math.max(0, rows.size() - rowCount));
      previous.visible = next.visible = rows.size() > rowCount;
      previous.active = offset > 0;
      next.active = offset + rowCount < rows.size();
      for (int i = 0; i < buttons.size(); i++) {
        var entry = buttons.get(i);
        entry.visible = offset + i < rows.size();
        entry.active = entry.visible && !locked && canChoose;
        if (!entry.visible) continue;
        var row = rows.get(offset + i);
        entry.marked = !locked && row.id().equals(selected);
        entry.setCaption(
            row.name(), row.distance() < 0 ? "" : row.distance() + " " + text("blocks"));
      }
    }

    void render(GuiGraphics g) {
      ThidaGui.text(
          g,
          text(locked ? "column_members" : "column_candidates"),
          x + 2,
          top + 56,
          width - (previous.visible ? 39 : 4),
          ThidaHudTheme.ACTIVE);
      if (rows.isEmpty()) {
        ThidaHudTheme.menuTile(g, x, top + LIST_TOP, width, 17, false, false, true);
        ThidaGui.text(
            g,
            text(!loaded ? "loading" : locked ? "empty_members" : "empty_candidates"),
            x + 6,
            top + LIST_TOP + 4,
            width - 12,
            ThidaHudTheme.SECONDARY);
      }
    }
  }

  private enum Tone {
    PLAIN,
    TAB,
    ROW,
    PRIMARY,
    DANGER
  }

  /** Vanilla button behavior and narration with the roster's own visual states. */
  private static final class RosterButton extends Button {
    private final Tone tone;
    private String caption, detail = "", icon = "";
    private boolean marked, readOnly;

    RosterButton(String caption, int x, int y, int w, int h, Tone tone, Runnable action) {
      super(x, y, w, h, label(caption), unused -> action.run(), DEFAULT_NARRATION);
      this.caption = caption;
      this.tone = tone;
    }

    void setCaption(String caption, String detail) {
      this.caption = caption;
      this.detail = detail;
      setMessage(label(detail.isEmpty() ? caption : caption + " " + detail));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
      int x = getX(), y = getY(), w = getWidth(), h = getHeight();
      boolean hover = active && isHoveredOrFocused();
      boolean dark = tone == Tone.PRIMARY && active || tone == Tone.TAB && marked;
      int ink =
          dark
              ? ThidaHudTheme.ON_DARK
              : tone == Tone.DANGER || marked
                  ? ThidaHudTheme.ACTIVE
                  : active || readOnly ? ThidaHudTheme.INK : ThidaHudTheme.RESTING;
      if (dark) {
        g.fill(x, y, x + w, y + h, 0xFF54223B);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF9A305F : 0xFF792344);
      } else {
        ThidaHudTheme.menuTile(g, x, y, w, h, marked, hover, !active);
        if (tone == Tone.DANGER && active)
          g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, 0xFFBD6C7D);
      }
      if (hover && isFocused()) g.renderOutline(x, y, w, h, ThidaHudTheme.ACTIVE);
      int textY = y + (h - 9) / 2;
      if (tone == Tone.ROW || tone == Tone.TAB) {
        int start = x + (tone == Tone.TAB ? 24 : 8), right = x + w - 7;
        if (!icon.isEmpty()) ThidaGui.icon(g, icon, x + 5, y + (h - 16) / 2, 1);
        if (!detail.isEmpty()) {
          int detailWidth = Math.min(Math.max(0, w / 3), ThidaGui.width(detail));
          ThidaGui.fittedText(
              g,
              detail,
              right - detailWidth,
              textY,
              detailWidth,
              dark ? ThidaHudTheme.ON_DARK : ThidaHudTheme.RESTING);
          right -= detailWidth + 7;
        }
        ThidaGui.text(g, caption, start, textY, right - start, ink);
      } else {
        int textWidth = Math.max(0, Math.min(w - 8, ThidaGui.width(caption)));
        ThidaGui.fittedText(g, caption, x + (w - textWidth) / 2, textY, textWidth, ink);
      }
    }
  }

  private static String text(String key) {
    return ThidaMessages.text("message.thida.ruen." + key);
  }

  private static Component label(String text) {
    return ThidaGui.styled(Component.literal(text));
  }
}
