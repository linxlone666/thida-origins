package dev.thida;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only placement and scaling; previews never change server skill state. */
public class ThidaHudEditor extends Screen {
  private enum Panel {
    BONDS,
    EFFECTS,
    RECIPIENT
  }

  private final Screen parent;
  private Panel selected = Panel.BONDS;
  private boolean dragging;
  private double offsetX, offsetY;

  public ThidaHudEditor(Screen parent) {
    super(Component.translatable("key.thida.edit_hud"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    int x = (width - 258) / 2, y = height - 27;
    addRenderableWidget(
        Button.builder(Component.literal("−"), b -> resize(-.05)).bounds(x, y, 28, 20).build());
    addRenderableWidget(
        Button.builder(Component.literal("+"), b -> resize(.05)).bounds(x + 32, y, 28, 20).build());
    addRenderableWidget(
        Button.builder(Component.translatable("hud.thida.reset"), b -> reset())
            .bounds(x + 64, y, 102, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("hud.thida.done"), b -> onClose())
            .bounds(x + 170, y, 88, 20)
            .build());
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    // The editor draws its own tint. Screen.render must not blur the HUD a second time.
  }

  private ThidaHudLayout.Bounds bounds(Panel panel) {
    return switch (panel) {
      case BONDS -> ThidaBondHud.bondBounds(true);
      case EFFECTS -> ThidaHud.previewBounds();
      case RECIPIENT -> ThidaBondHud.recipientBounds();
    };
  }

  private ModConfigSpec.DoubleValue scale() {
    return switch (selected) {
      case BONDS -> ThidaHudConfig.BOND_SCALE;
      case EFFECTS -> ThidaHudConfig.EFFECT_SCALE;
      case RECIPIENT -> ThidaHudConfig.RECIPIENT_SCALE;
    };
  }

  private void resize(double amount) {
    scale().set(Math.clamp(Math.round((scale().get() + amount) * 100) / 100.0, .6, 1.4));
  }

  private void reset() {
    switch (selected) {
      case BONDS -> {
        ThidaHudConfig.BOND_X.set(8);
        ThidaHudConfig.BOND_Y.set(8);
        scale().set(1.0);
      }
      case EFFECTS -> {
        ThidaHudConfig.X.set(8);
        ThidaHudConfig.Y.set(8);
        scale().set(.9);
      }
      case RECIPIENT -> {
        ThidaHudConfig.RECIPIENT_X.set(0);
        ThidaHudConfig.RECIPIENT_Y.set(-80);
        scale().set(.9);
      }
    }
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    g.fill(0, 0, width, height, 0x45000000);
    ThidaBondHud.drawBonds(g, true);
    ThidaHud.draw(g, true);
    ThidaBondHud.drawRecipient(g, true);
    var area = bounds(selected);
    g.renderOutline(area.x() - 2, area.y() - 2, area.width() + 4, area.height() + 4, ThidaGui.GOLD);
    String panel =
        ThidaMessages.text("hud.thida.panel." + selected.name().toLowerCase(java.util.Locale.ROOT));
    String hint =
        panel
            + " · "
            + Math.round(scale().get() * 100)
            + "% · "
            + ThidaMessages.text("hud.thida.editor.hint");
    int hintWidth = Math.min(width - 16, minecraft.font.width(hint) + 16);
    ThidaGui.hudPanel(g, (width - hintWidth) / 2, height - 47, hintWidth, 16, ThidaGui.GOLD);
    ThidaGui.text(g, hint, (width - hintWidth) / 2 + 8, height - 43, hintWidth - 16, ThidaGui.TEXT);
    super.render(g, mouseX, mouseY, partial);
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    if (button == 1) {
      onClose();
      return true;
    }
    if (super.mouseClicked(mx, my, button)) return true;
    if (button == 0) {
      for (Panel panel : new Panel[] {Panel.RECIPIENT, Panel.EFFECTS, Panel.BONDS}) {
        var area = bounds(panel);
        if (area.contains(mx, my)) {
          selected = panel;
          dragging = true;
          offsetX = mx - area.x();
          offsetY = my - area.y();
          break;
        }
      }
    }
    return true;
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
    if (vertical != 0) resize(vertical > 0 ? .05 : -.05);
    return true;
  }

  @Override
  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (!dragging || button != 0) return super.mouseDragged(mx, my, button, dx, dy);
    var area = bounds(selected);
    int x = (int) Math.clamp(mx - offsetX, 0, Math.max(0, width - area.width()));
    int y = (int) Math.clamp(my - offsetY, 0, Math.max(0, height - area.height()));
    switch (selected) {
      case BONDS -> {
        ThidaHudConfig.BOND_X.set(x);
        ThidaHudConfig.BOND_Y.set(y);
      }
      case EFFECTS -> {
        ThidaHudConfig.X.set(x);
        ThidaHudConfig.Y.set(y);
      }
      case RECIPIENT -> {
        ThidaHudConfig.RECIPIENT_X.set(x - (width - area.width()) / 2);
        ThidaHudConfig.RECIPIENT_Y.set(y - height);
      }
    }
    return true;
  }

  @Override
  public boolean mouseReleased(double mx, double my, int button) {
    dragging = false;
    return super.mouseReleased(mx, my, button);
  }

  @Override
  public void removed() {
    ThidaHudConfig.BOND_X.save();
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
