package dev.isxander.yacl3.gui.tab;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.gui.utils.GuiUtils;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VerticalNavigationBar extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
	private static final int NAVBAR_MARGIN = 28; // Margin to show the scrollability of the navbar
	private static final int NO_TAB = -1;
	public static final int MAX_WIDTH = 100;
	private static final Component USAGE_NARRATION = Component.translatable("narration.tab_navigation.usage");
	private int scrollOffset;
	private int maxScrollOffset;
	private final int height;
	private final LinearLayout layout = LinearLayout.vertical();
	private final TabManager tabManager;
	private final ImmutableList<Tab> tabs;
	private final ImmutableList<TabButton> tabButtons;

	public VerticalNavigationBar(int height, TabManager tabManager, Iterable<Tab> tabs) {
		this.height = height;
		this.tabManager = tabManager;
		this.tabs = ImmutableList.copyOf(tabs);
		this.layout.defaultCellSetting().alignHorizontallyCenter();
		ImmutableList.Builder<TabButton> builder = ImmutableList.builder();

		for (Tab tab : tabs) {
			builder.add(this.layout.addChild(new TabButton(tabManager, tab, MAX_WIDTH, 24)));
		}

		this.tabButtons = builder.build();

		for (TabButton tabButton : tabButtons) {
			if (tabButton.tab() instanceof TabExt tab) {
				tabButton.setTooltip(tab.getTooltip());
			}
		}
	}

	@Nullable
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		if (!this.isFocused()) {
			TabButton tabButton = this.currentTabButton();
			if (tabButton != null) {
				return ComponentPath.path(this, ComponentPath.leaf(tabButton));
			}
		}

		return event instanceof FocusNavigationEvent.TabNavigation ? null : super.nextFocusPath(event);
	}

	@Override
	@NotNull
	public List<? extends GuiEventListener> children() {
		return this.tabButtons;
	}

	@Override
	@NotNull
	public NarratableEntry.NarrationPriority narrationPriority() {
		return this.tabButtons.stream().map(AbstractWidget::narrationPriority).max(Comparator.naturalOrder()).orElse(NarrationPriority.NONE);
	}

	public void updateNarration(NarrationElementOutput narrationElementOutput) {
		Optional<TabButton> optional = this.tabButtons.stream().filter(AbstractWidget::isHovered).findFirst().or(() -> Optional.ofNullable(this.currentTabButton()));
		optional.ifPresent(tabButton -> {
			this.narrateListElementPosition(narrationElementOutput.nest(), tabButton);
			tabButton.updateNarration(narrationElementOutput);
		});
		if (this.isFocused()) {
			narrationElementOutput.add(NarratedElementType.USAGE, USAGE_NARRATION);
		}
	}

	protected void narrateListElementPosition(NarrationElementOutput narrationElementOutput, TabButton tabButton) {
		if (this.tabs.size() > 1) {
			int i = this.tabButtons.indexOf(tabButton);
			if (i != NO_TAB) {
				narrationElementOutput.add(NarratedElementType.POSITION, Component.translatable("narrator.position.tab", i + 1, this.tabs.size()));
			}
		}
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		GuiUtils.pushPose(graphics);
		// render option list BELOW the navbar without need to scissor
		GuiUtils.translateZ(graphics, 10);

		for (TabButton tabButton : this.tabButtons) {
			tabButton.render(graphics, mouseX, mouseY, partialTick);
		}

		GuiUtils.popPose(graphics);
	}

	@Override
	@NotNull
	public ScreenRectangle getRectangle() {
		return this.layout.getRectangle();
	}

	public void arrangeElements() {
		int allTabsHeight = 0;
		for (TabButton tabButton : this.tabButtons) {
			tabButton.setWidth(MAX_WIDTH);
			allTabsHeight += tabButton.getHeight();
		}

		this.layout.arrangeElements();
		this.layout.setX(0);
		this.layout.setY(0);
		this.maxScrollOffset = Math.max(0, allTabsHeight - this.height);
	}

	public void selectTab(int index, boolean playClickSound) {
		if (this.isFocused()) {
			this.setFocused(this.tabButtons.get(index));
		} else {
			this.tabManager.setCurrentTab(this.tabs.get(index), playClickSound);
		}
	}

	@Override
	public boolean keyPressed(int keycode, int scanCode, int modifiers) {
		if (Screen.hasControlDown()) {
			int i = this.getNextTabIndex(keycode);
			if (i != NO_TAB) {
				this.selectTab(Mth.clamp(i, 0, this.tabs.size() - 1), true);
				return true;
			}
		}

		return false;
	}

	private int getNextTabIndex(int keycode) {
		if (keycode >= GLFW.GLFW_KEY_1 && keycode <= GLFW.GLFW_KEY_9) {
			return keycode - 49; //Convert to 0-8 range
		} else {
			if (keycode == GLFW.GLFW_KEY_TAB) {
				int i = this.currentTabIndex();
				if (i != NO_TAB) {
					int j = Screen.hasShiftDown() ? i - 1 : i + 1;
					return Math.floorMod(j, this.tabs.size());
				}
			}

			return NO_TAB;
		}
	}

	private int currentTabIndex() {
		Tab tab = this.tabManager.getCurrentTab();
		return this.tabs.indexOf(tab);
	}

	@Nullable
	private TabButton currentTabButton() {
		int i = this.currentTabIndex();
		return i != NO_TAB ? this.tabButtons.get(i) : null;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX <= MAX_WIDTH;
	}

	public void setScrollOffset(int scrollOffset) {
		layout.setY(layout.getY() + this.scrollOffset);
		this.scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset);
		layout.setY(layout.getY() - this.scrollOffset);
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		this.setScrollOffset(this.scrollOffset - (int) vertical);
		return true;
	}

	public TabManager getTabManager() {
		return tabManager;
	}

	public ImmutableList<Tab> getTabs() {
		return tabs;
	}

	@Override
	public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (this.getFocused() != null) {
            this.getFocused().setFocused(focused);
        }
    }

	@Override
	public void setFocused(@Nullable GuiEventListener child) {
		super.setFocused(child);
		if (child instanceof TabButton tabButton) {
			this.tabManager.setCurrentTab(tabButton.tab(), true);
			this.ensureVisible(tabButton);
		}
	}

	protected void ensureVisible(TabButton tabButton) {
		if (tabButton.getY() < NAVBAR_MARGIN) {
			this.setScrollOffset(this.scrollOffset - (NAVBAR_MARGIN - tabButton.getY()));
		} else if (tabButton.getBottom() > height - NAVBAR_MARGIN) {
			this.setScrollOffset(this.scrollOffset + tabButton.getBottom() - (height - NAVBAR_MARGIN));
		}
	}
}
