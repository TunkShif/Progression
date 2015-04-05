package joshie.crafting.gui;

import java.util.ArrayList;

import joshie.crafting.CraftAPIRegistry;
import joshie.crafting.api.CraftingAPI;
import joshie.crafting.api.ITab;
import joshie.crafting.gui.SelectItemOverlay.Type;
import joshie.crafting.gui.SelectTextEdit.ITextEditable;
import joshie.crafting.helpers.ClientHelper;
import joshie.crafting.helpers.StackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class ButtonTab extends ButtonBase implements ITextEditable, IItemSelectable {
    public static enum Side {
        LEFT, RIGHT;
    }

    private ITab tab;

    public ButtonTab(ITab tab, int x, int y) {
        super(0, x, y, 25, 25, "");
        this.tab = tab;
    }

    @Override
    public void drawButton(Minecraft mc, int x, int y) {
        boolean hovering = field_146123_n = x >= xPosition && y >= yPosition && x < xPosition + width && y < yPosition + height;
        int k = getHoverState(hovering);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(textures);
        int yTexture = GuiTreeEditor.INSTANCE.currentTab == tab ? 24 : 0;
        RenderHelper.disableStandardItemLighting();
        int xTexture = 206;
        if (xPosition == 0) xTexture = 231;
        drawTexturedModalRect(xPosition, yPosition, xTexture, yTexture, 25, 25);
        if (xPosition == 0) {
            StackHelper.drawStack(tab.getStack(), xPosition + 2, yPosition + 5, 1F);
        } else StackHelper.drawStack(tab.getStack(), xPosition + 7, yPosition + 5, 1F);

        boolean displayTooltip = false;
        if (ClientHelper.canEdit()) {
            displayTooltip = SelectTextEdit.INSTANCE.getEditable() == this;
        }

        if (k == 2 || displayTooltip) {
            ArrayList<String> name = new ArrayList();
            String hidden = tab.isVisible() ? "" : "(Hidden)";
            name.add(SelectTextEdit.INSTANCE.getText(this) + hidden);
            if (ClientHelper.canEdit()) {
                name.add(EnumChatFormatting.GRAY + "(Sort Index) " + tab.getSortIndex());
                name.add(EnumChatFormatting.GRAY + "Shift + Click to rename");
                name.add(EnumChatFormatting.GRAY + "Ctrl + Click to select item icon");
                name.add(EnumChatFormatting.GRAY + "I + Click to hide/unhide");
                name.add(EnumChatFormatting.GRAY + "Arrow keys to move up/down");
                name.add(EnumChatFormatting.GRAY + "Delete + Click to delete");
                name.add(EnumChatFormatting.RED + "  Deleting a tab, deletes all criteria in it");
            }

            GuiTreeEditor.INSTANCE.tooltip.clear();
            GuiTreeEditor.INSTANCE.addTooltip(name);
        }
    }

    @Override
    public void onClicked() {
        //If the tab is already selected, then we should edit it instead        
        if (ClientHelper.canEdit()) {
            if (Keyboard.isKeyDown(Keyboard.KEY_DELETE)) {
                ITab newTab = GuiTreeEditor.INSTANCE.currentTab;
                if (tab == GuiTreeEditor.INSTANCE.currentTab) {
                    newTab = GuiTreeEditor.INSTANCE.previousTab;
                }

                if (newTab != null) {
                    if (!CraftAPIRegistry.tabs.containsKey(newTab.getUniqueName())) {
                        for (ITab tab : CraftAPIRegistry.tabs.values()) {
                            newTab = tab;
                            break;
                        }
                    }
                }

                if (newTab == null) {
                    newTab = CraftingAPI.registry.newTab(CraftAPIRegistry.getNextUnique()).setDisplayName("New Tab").setStack(new ItemStack(Items.book)).setVisibility(true);
                }

                GuiTreeEditor.INSTANCE.selected = null;
                GuiTreeEditor.INSTANCE.previous = null;
                GuiTreeEditor.INSTANCE.lastClicked = null;
                GuiTreeEditor.INSTANCE.currentTab = newTab;
                GuiTreeEditor.INSTANCE.currentTabName = newTab.getUniqueName();
                CraftAPIRegistry.removeTab(tab);
                GuiTreeEditor.INSTANCE.initGui();
                return;
            }

            if (GuiScreen.isShiftKeyDown()) {
                SelectTextEdit.INSTANCE.select(this);
            } else if (GuiScreen.isCtrlKeyDown()) {
                SelectItemOverlay.INSTANCE.select(this, Type.TREE);
            } else if (Keyboard.isKeyDown(Keyboard.KEY_I)) {
                boolean current = tab.isVisible();
                tab.setVisibility(!current);
            }
        }

        GuiTreeEditor.INSTANCE.previousTab = GuiTreeEditor.INSTANCE.currentTab;
        GuiTreeEditor.INSTANCE.currentTab = tab;
        GuiTreeEditor.INSTANCE.currentTabName = tab.getUniqueName();
    }

    @Override
    public void onNotClicked() {
        if (ClientHelper.canEdit()) {
            if (SelectTextEdit.INSTANCE.getEditable() == this) {
                SelectTextEdit.INSTANCE.clear();
            }

            if (SelectItemOverlay.INSTANCE.getEditable() == this) {
                SelectItemOverlay.INSTANCE.clear();
            }
        }
    }

    @Override
    public String getTextField() {
        return tab.getDisplayName();
    }

    @Override
    public void setTextField(String str) {
        tab.setDisplayName(str);
    }

    @Override
    public void setItemStack(ItemStack stack) {
        tab.setStack(stack);
    }
}
