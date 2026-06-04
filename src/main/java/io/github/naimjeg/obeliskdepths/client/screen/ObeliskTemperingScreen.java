package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPoolRegistry;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPreviewResolver;
import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import io.github.naimjeg.obeliskdepths.tempering.TemperingDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class ObeliskTemperingScreen
        extends AbstractContainerScreen<ObeliskTemperingMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "textures/gui/container/obelisk_tempering.png"
            );

    private static final int DIRECTION_LIST_X = 52;
    private static final int DIRECTION_LIST_Y = 32;
    private static final int DIRECTION_LIST_WIDTH = 72;
    private static final int DIRECTION_LIST_HEIGHT = 78;
    private static final int AFFIX_LIST_X = 132;
    private static final int AFFIX_LIST_Y = 32;
    private static final int AFFIX_LIST_WIDTH = 104;
    private static final int AFFIX_LIST_HEIGHT = 78;

    private DirectionSelectionList directionList;
    private AffixPreviewList affixList;
    private ItemStack lastWeapon = ItemStack.EMPTY;
    private ItemStack lastTemplate = ItemStack.EMPTY;
    private ItemStack lastIngredient = ItemStack.EMPTY;
    private TemperingDirection lastDirection = TemperingDirection.BALANCE;
    private boolean lastValidRecipe;
    private int lastPoolHash;

    public ObeliskTemperingScreen(
            ObeliskTemperingMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title, 248, 196);
    }

    @Override
    protected void init() {
        super.init();

        this.directionList = this.addRenderableWidget(new DirectionSelectionList(
                this.minecraft,
                this.leftPos + DIRECTION_LIST_X,
                this.topPos + DIRECTION_LIST_Y,
                DIRECTION_LIST_WIDTH,
                DIRECTION_LIST_HEIGHT
        ));
        this.affixList = this.addRenderableWidget(new AffixPreviewList(
                this.minecraft,
                this.leftPos + AFFIX_LIST_X,
                this.topPos + AFFIX_LIST_Y,
                AFFIX_LIST_WIDTH,
                AFFIX_LIST_HEIGHT
        ));

        this.refreshPreview(true);
    }

    @Override
    protected void containerTick() {
        this.refreshPreview(false);
    }

    private void refreshPreview(boolean force) {
        ItemStack weapon = this.menu
                .getSlot(ObeliskTemperingMenu.WEAPON_SLOT)
                .getItem();
        ItemStack template = this.menu
                .getSlot(ObeliskTemperingMenu.TEMPLATE_SLOT)
                .getItem();
        ItemStack ingredient = this.menu
                .getSlot(ObeliskTemperingMenu.INGREDIENT_SLOT)
                .getItem();
        TemperingDirection direction = this.menu.selectedDirection();
        boolean validRecipe = this.menu.hasValidRecipe();
        int poolHash = this.menu.currentPoolHash();
        boolean changed = force
                || !ItemStack.matches(weapon, this.lastWeapon)
                || !ItemStack.matches(template, this.lastTemplate)
                || !ItemStack.matches(ingredient, this.lastIngredient)
                || direction != this.lastDirection
                || validRecipe != this.lastValidRecipe
                || poolHash != this.lastPoolHash;

        if (!changed) {
            return;
        }

        this.lastWeapon = weapon.copy();
        this.lastTemplate = template.copy();
        this.lastIngredient = ingredient.copy();
        this.lastDirection = direction;
        this.lastValidRecipe = validRecipe;
        this.lastPoolHash = poolHash;

        if (this.directionList != null) {
            this.directionList.setSelectedDirection(direction);
        }

        if (this.affixList == null) {
            return;
        }

        boolean previewAvailable = validRecipe
                && ObeliskTemperingPoolRegistry.findPoolByPreviewHash(poolHash)
                .isPresent();
        List<TemperingAffixPreview> previews = List.of();

        if (validRecipe && this.minecraft != null
                && this.minecraft.level != null
                && previewAvailable) {
            previews = ObeliskTemperingPreviewResolver.resolveClientPreview(
                    this.minecraft.level,
                    this.menu.createRecipeInput(),
                    poolHash
            );
        }

        this.affixList.setPreviews(
                previews,
                validRecipe,
                previewAvailable
        );
    }

    private void selectDirection(TemperingDirection direction) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId,
                    direction.id()
            );
        }
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                this.imageWidth,
                this.imageHeight,
                256,
                256
        );
    }

    @Override
    protected void extractLabels(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.text(
                this.font,
                this.title,
                this.titleLabelX,
                this.titleLabelY,
                0x404040,
                false
        );
        graphics.text(
                this.font,
                Component.translatable("gui.obeliskdepths.tempering.directions"),
                DIRECTION_LIST_X,
                20,
                0x404040,
                false
        );
        graphics.text(
                this.font,
                Component.translatable("gui.obeliskdepths.tempering.possible_affixes"),
                AFFIX_LIST_X,
                20,
                0x404040,
                false
        );
        graphics.text(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0x404040,
                false
        );

        if (this.menu.hasRecipeError()) {
            graphics.text(
                    this.font,
                    Component.translatable("gui.obeliskdepths.tempering.invalid_recipe"),
                    52,
                    114,
                    0xA00000,
                    false
            );
        }
    }

    private final class DirectionSelectionList
            extends ObjectSelectionList<DirectionSelectionList.Entry> {
        private DirectionSelectionList(
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height
        ) {
            super(minecraft, width, height, y, 18);
            this.updateSizeAndPosition(width, height, x, y);
            this.replaceEntries(Arrays.stream(TemperingDirection.values())
                    .map(direction -> new Entry(direction))
                    .toList());
            this.setSelectedDirection(ObeliskTemperingScreen.this.menu
                    .selectedDirection());
        }

        @Override
        public int getRowWidth() {
            return Math.max(16, this.getWidth() - 16);
        }

        private void setSelectedDirection(TemperingDirection direction) {
            for (Entry entry : this.children()) {
                if (entry.direction == direction) {
                    this.setSelected(entry);
                    return;
                }
            }
        }

        private final class Entry
                extends ObjectSelectionList.Entry<DirectionSelectionList.Entry> {
            private final TemperingDirection direction;

            private Entry(TemperingDirection direction) {
                this.direction = direction;
            }

            @Override
            public void extractContent(
                    GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    boolean hovered,
                    float partialTick
            ) {
                boolean available = ObeliskTemperingScreen.this.menu
                        .isDirectionAvailable(this.direction);
                int color = available ? 0xFFFFFF : 0x707070;

                if (hovered && available) {
                    graphics.fill(
                            this.getX() + 1,
                            this.getY() + 1,
                            this.getX() + this.getWidth() - 1,
                            this.getY() + this.getHeight() - 1,
                            0x553C6A74
                    );
                }

                graphics.text(
                        ObeliskTemperingScreen.this.font,
                        this.direction.displayName(),
                        this.getContentX(),
                        this.getContentYMiddle()
                                - ObeliskTemperingScreen.this.font.lineHeight / 2,
                        color,
                        false
                );
            }

            @Override
            public boolean mouseClicked(
                    MouseButtonEvent event,
                    boolean doubleClick
            ) {
                if (event.button() == 0
                        && ObeliskTemperingScreen.this.menu
                        .isDirectionAvailable(this.direction)) {
                    DirectionSelectionList.this.setSelected(this);
                    ObeliskTemperingScreen.this.selectDirection(this.direction);
                    return true;
                }

                return false;
            }

            @Override
            public Component getNarration() {
                return Component.translatable(
                        "narrator.select",
                        this.direction.displayName()
                );
            }
        }
    }

    private final class AffixPreviewList
            extends ObjectSelectionList<AffixPreviewList.Entry> {
        private AffixPreviewList(
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height
        ) {
            super(minecraft, width, height, y, 24);
            this.updateSizeAndPosition(width, height, x, y);
        }

        @Override
        public int getRowWidth() {
            return Math.max(16, this.getWidth() - 16);
        }

        private void setPreviews(
                List<TemperingAffixPreview> previews,
                boolean validRecipe,
                boolean previewAvailable
        ) {
            if (!validRecipe) {
                this.replaceEntries(List.of());
                return;
            }

            if (!previewAvailable) {
                this.replaceEntries(List.of(this.messageEntry(Component.translatable(
                        "gui.obeliskdepths.tempering.preview_unavailable"
                ))));
                return;
            }

            if (previews.isEmpty()) {
                this.replaceEntries(List.of(this.messageEntry(Component.translatable(
                        "gui.obeliskdepths.tempering.no_possible_affixes"
                ))));
                return;
            }

            this.replaceEntries(previews
                    .stream()
                    .map(this::previewEntry)
                    .toList());
        }

        private Entry messageEntry(Component message) {
            return new Entry(
                    message,
                    Component.empty(),
                    0,
                    true
            );
        }

        private Entry previewEntry(TemperingAffixPreview preview) {
            Component description = preview.description();

            if (description == null) {
                description = Component.empty();
            }

            return new Entry(
                    preview.displayName(),
                    description,
                    preview.weight(),
                    false
            );
        }

        private final class Entry
                extends ObjectSelectionList.Entry<AffixPreviewList.Entry> {
            private final Component name;
            private final Component description;
            private final int weight;
            private final boolean message;

            private Entry(
                    Component name,
                    Component description,
                    int weight,
                    boolean message
            ) {
                this.name = name;
                this.description = description;
                this.weight = weight;
                this.message = message;
            }

            @Override
            public void extractContent(
                    GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    boolean hovered,
                    float partialTick
            ) {
                if (hovered && !this.message) {
                    graphics.fill(
                            this.getX() + 1,
                            this.getY() + 1,
                            this.getX() + this.getWidth() - 1,
                            this.getY() + this.getHeight() - 1,
                            0x553C6A74
                    );
                }

                graphics.text(
                        ObeliskTemperingScreen.this.font,
                        this.name,
                        this.getContentX(),
                        this.getContentY(),
                        this.message ? 0xB0B0B0 : 0xFFFFFF,
                        false
                );

                if (!this.message) {
                    Component detail = this.description.getString().isBlank()
                            ? Component.literal("Weight: " + this.weight)
                            : this.description;

                    graphics.text(
                            ObeliskTemperingScreen.this.font,
                            detail,
                            this.getContentX(),
                            this.getContentY() + 10,
                            0xB0B0B0,
                            false
                    );
                }
            }

            @Override
            public boolean mouseClicked(
                    MouseButtonEvent event,
                    boolean doubleClick
            ) {
                return false;
            }

            @Override
            public Component getNarration() {
                return this.name;
            }
        }
    }
}
