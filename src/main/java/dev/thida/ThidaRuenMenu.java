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
  private static final int ACCENT = 0xFF91DAC2;

  private record Row(UUID id, String name, int distance) {}

  private final UUID activation;
  private final List<Button> rowButtons = new ArrayList<>();
  private CompoundTag latest;
  private List<Row> rows = List.of();
  private String side = "inside", feedback = "";
  private UUID selected;
  private boolean viewingMembers, loaded;
  private int offset, ticks, pendingUntil;
  private int left, top, panelWidth, panelHeight, rowCount;
  private Button inside, outside, toggle, previous, next, aim, confirm;

  ThidaRuenMenu(UUID activation) {
    super(label(text("title")));
    this.activation = activation;
    ThidaHudState.acceptRuen(null);
  }

  @Override
  protected void init() {
    rowButtons.clear();
    panelWidth = Math.min(width - 12, 288);
    rowCount = Math.clamp((height - 116) / ROW_HEIGHT, 1, 6);
    panelHeight = 104 + rowCount * ROW_HEIGHT;
    left = (width - panelWidth) / 2;
    top = Math.max(2, (height - panelHeight) / 2);
    int x = left + 8, usable = panelWidth - 16, half = (usable - 4) / 2;
    inside = button("", x, top + 20, half, () -> changeSide("inside"));
    outside = button("", x + half + 4, top + 20, usable - half - 4, () -> changeSide("outside"));
    previous = button("‹", x, top + 40, 22, () -> move(-rowCount));
    next = button("›", x + usable - 22, top + 40, 22, () -> move(rowCount));
    toggle =
        button(
            "",
            x + 26,
            top + 40,
            usable - 52,
            () -> {
              viewingMembers = !viewingMembers;
              resetSelection();
              refresh();
            });
    for (int i = 0; i < rowCount; i++) {
      final int slot = i;
      rowButtons.add(
          button(
              "",
              x,
              top + 60 + i * ROW_HEIGHT,
              usable,
              () -> {
                if (!viewingMembers && offset + slot < rows.size()) {
                  selected = rows.get(offset + slot).id();
                  feedback = "";
                  refresh();
                }
              }));
    }
    int bottom = top + panelHeight - 24, third = (usable - 8) / 3;
    aim =
        button(
            text("aim_button"),
            x,
            bottom,
            third,
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
        () -> {
          ThidaClient.ruenRequest("ruen_cancel", activation, "", null);
          onClose();
        });
    refresh();
    requestRoster();
  }

  private Button button(String name, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(label(name), unused -> action.run()).bounds(x, y, w, 17).build());
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
    side = nextSide;
    viewingMembers = false;
    resetSelection();
    refresh();
  }

  private void resetSelection() {
    offset = 0;
    selected = null;
    if (pendingUntil == 0) feedback = "";
  }

  private void move(int delta) {
    offset = Math.clamp(offset + delta, 0, Math.max(0, rows.size() - rowCount));
    refresh();
  }

  private int count(String team) {
    if (latest != null) return latest.getList(team, Tag.TAG_COMPOUND).size();
    return ThidaHudState.snapshot().getInt(team.equals("inside") ? "ruenInside" : "ruenOutside");
  }

  private void refresh() {
    var updated = new ArrayList<Row>();
    if (latest != null) {
      var list = latest.getList(viewingMembers ? side : "candidates", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        var entry = list.getCompound(i);
        if (entry.hasUUID("id"))
          updated.add(
              new Row(entry.getUUID("id"), entry.getString("name"), entry.getInt("distance")));
      }
    }
    rows = updated;
    offset = Math.min(offset, Math.max(0, rows.size() - rowCount));
    if (selected != null && selectedRow() == null && pendingUntil == 0) {
      selected = null;
      feedback = "no_target";
    }
    inside.setMessage(label(teamLabel("inside")));
    outside.setMessage(label(teamLabel("outside")));
    toggle.setMessage(label(text(viewingMembers ? "show_available" : "show_members")));
    previous.active = offset > 0;
    next.active = offset + rowCount < rows.size();
    boolean canChoose = count(side) < ThidaRuenSaneha.CAPACITY && pendingUntil == 0;
    aim.active = canChoose;
    confirm.active = canChoose && !viewingMembers && selectedRow() != null;
    for (int i = 0; i < rowButtons.size(); i++) {
      var button = rowButtons.get(i);
      button.visible = offset + i < rows.size();
      button.active = button.visible && !viewingMembers && canChoose;
      if (!button.visible) continue;
      var row = rows.get(offset + i);
      String rowText = (row.id().equals(selected) ? "› " : "") + row.name();
      if (row.distance() >= 0) rowText += "   " + row.distance() + " " + text("blocks");
      button.setMessage(label(rowText));
    }
  }

  private String teamLabel(String team) {
    return (side.equals(team) ? "› " : "")
        + text(team)
        + " "
        + count(team)
        + "/"
        + ThidaRuenSaneha.CAPACITY;
  }

  private Row selectedRow() {
    for (var row : rows) if (row.id().equals(selected)) return row;
    return null;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return false;
    move(vertical < 0 ? 1 : -1);
    return true;
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
    ThidaGui.frame(g, left, top, panelWidth, panelHeight, ACCENT, true);
    ThidaGui.text(g, text("title"), left + 8, top + 6, panelWidth - 60, ThidaGui.TEXT);
    String time =
        ThidaHudState.time(ThidaHudState.remaining(ThidaHudState.snapshot().getLong("domain")));
    ThidaGui.fittedText(g, time, left + panelWidth - 43, top + 6, 35, ThidaGui.GOLD);
    if (rows.isEmpty())
      ThidaGui.text(
          g,
          text(!loaded ? "loading" : viewingMembers ? "empty_members" : "empty_candidates"),
          left + 10,
          top + 63,
          panelWidth - 20,
          ThidaGui.MUTED);
    Row row = selectedRow();
    String status =
        pendingUntil != 0 || !feedback.isEmpty()
            ? text(feedback)
            : row == null
                ? text(viewingMembers ? "roster_locked" : "pick_hint")
                : text("confirm_to") + " " + text(side) + ": " + row.name();
    ThidaGui.text(g, status, left + 8, top + panelHeight - 39, panelWidth - 16, ThidaGui.GOLD);
    super.render(g, mouseX, mouseY, partial);
  }

  private static String text(String key) {
    return ThidaMessages.text("message.thida.ruen." + key);
  }

  private static Component label(String text) {
    return ThidaGui.styled(Component.literal(text));
  }
}
