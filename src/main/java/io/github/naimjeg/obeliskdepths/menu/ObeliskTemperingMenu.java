package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDataComponents;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import io.github.naimjeg.obeliskdepths.registry.ModRecipeTypes;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPoolRegistry;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingRoller;
import io.github.naimjeg.obeliskdepths.tempering.TemperingDirection;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public class ObeliskTemperingMenu extends AbstractContainerMenu {
    public static final int WEAPON_SLOT = 0;
    public static final int TEMPLATE_SLOT = 1;
    public static final int INGREDIENT_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    public static final int WEAPON_SLOT_X = 18;
    public static final int WEAPON_SLOT_Y = 22;
    public static final int TEMPLATE_SLOT_X = 43;
    public static final int TEMPLATE_SLOT_Y = 22;
    public static final int INGREDIENT_SLOT_X = 30;
    public static final int INGREDIENT_SLOT_Y = 48;
    // TODO: Replace this temporary output slot with the final explicit apply control.
    public static final int RESULT_SLOT_X = 152;
    public static final int RESULT_SLOT_Y = 48;

    public static final int AFFIX_BUTTON_OFFSET = 1000;

    private static final int PLAYER_INVENTORY_START = 4;
    private static final int PLAYER_INVENTORY_END = 31;
    private static final int PLAYER_HOTBAR_END = 40;

    private final ContainerLevelAccess access;
    private final Level level;
    private final SimpleContainer inputSlots;
    private final ResultContainer resultSlots = new ResultContainer();

    private final DataSlot selectedDirection = DataSlot.standalone();
    private final DataSlot hasRecipeError = DataSlot.standalone();
    private final DataSlot hasValidRecipe = DataSlot.standalone();
    private final DataSlot currentPoolHash = DataSlot.standalone();
    private final DataSlot availableDirectionMask = DataSlot.standalone();

    public ObeliskTemperingMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ObeliskTemperingMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access
    ) {
        super(ModMenuTypes.OBELISK_TEMPERING.get(), containerId);

        this.access = access;
        this.level = inventory.player.level();
        this.inputSlots = new SimpleContainer(3) {
            @Override
            public void setChanged() {
                super.setChanged();
                ObeliskTemperingMenu.this.slotsChanged(this);
            }
        };

        this.addSlot(new Slot(
                this.inputSlots,
                WEAPON_SLOT,
                WEAPON_SLOT_X,
                WEAPON_SLOT_Y
        ));
        this.addSlot(new Slot(
                this.inputSlots,
                TEMPLATE_SLOT,
                TEMPLATE_SLOT_X,
                TEMPLATE_SLOT_Y
        ));
        this.addSlot(new Slot(
                this.inputSlots,
                INGREDIENT_SLOT,
                INGREDIENT_SLOT_X,
                INGREDIENT_SLOT_Y
        ));
        this.addSlot(new ObeliskTemperingResultSlot(
                this.resultSlots,
                0,
                RESULT_SLOT_X,
                RESULT_SLOT_Y
        ));

        this.addStandardInventorySlots(inventory, 7, 84);

        this.addDataSlot(this.selectedDirection)
                .set(TemperingDirection.BALANCE.id());
        this.addDataSlot(this.hasRecipeError).set(0);
        this.addDataSlot(this.hasValidRecipe).set(0);
        this.addDataSlot(this.currentPoolHash).set(0);
        this.addDataSlot(this.availableDirectionMask).set(allDirectionMask());
    }

    public ObeliskTemperingRecipeInput createRecipeInput() {
        return this.createRecipeInput(this.selectedDirection());
    }

    private ObeliskTemperingRecipeInput createRecipeInput(
            TemperingDirection direction
    ) {
        return new ObeliskTemperingRecipeInput(
                this.inputSlots.getItem(WEAPON_SLOT),
                this.inputSlots.getItem(TEMPLATE_SLOT),
                this.inputSlots.getItem(INGREDIENT_SLOT),
                direction
        );
    }

    public Optional<RecipeHolder<ObeliskTemperingRecipe>> findCurrentRecipe() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }

        return serverLevel.recipeAccess().getRecipeFor(
                ModRecipeTypes.OBELISK_TEMPERING.get(),
                this.createRecipeInput(),
                serverLevel
        );
    }

    public void createResult() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        this.updateAvailableDirections(serverLevel);

        Optional<RecipeHolder<ObeliskTemperingRecipe>> foundRecipe =
                this.findCurrentRecipe();

        if (foundRecipe.isPresent()) {
            RecipeHolder<ObeliskTemperingRecipe> recipe = foundRecipe.get();
            ItemStack result = recipe.value().assemble(this.createRecipeInput());

            if (!result.isEmpty()) {
                this.resultSlots.setRecipeUsed(recipe);
                this.resultSlots.setItem(0, result);
                this.hasValidRecipe.set(1);
                this.currentPoolHash.set(
                        ObeliskTemperingPoolRegistry.previewHash(
                                recipe.value().pool()
                        )
                );
            } else {
                this.clearResult();
            }
        } else {
            this.clearResult();
        }

        this.updateRecipeErrorState();
        this.broadcastChanges();
    }

    private void clearResult() {
        this.resultSlots.setRecipeUsed(null);
        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.hasValidRecipe.set(0);
        this.currentPoolHash.set(0);
    }

    private void updateRecipeErrorState() {
        boolean hasWeapon = !this.inputSlots.getItem(WEAPON_SLOT).isEmpty();
        boolean hasTemplate = !this.inputSlots.getItem(TEMPLATE_SLOT).isEmpty();
        boolean hasResult = !this.resultSlots.getItem(0).isEmpty();

        this.hasRecipeError.set(
                hasWeapon && hasTemplate && !hasResult ? 1 : 0
        );
    }

    private void updateAvailableDirections(ServerLevel serverLevel) {
        int mask = 0;

        for (TemperingDirection direction : TemperingDirection.values()) {
            if (serverLevel.recipeAccess().getRecipeFor(
                    ModRecipeTypes.OBELISK_TEMPERING.get(),
                    this.createRecipeInput(direction),
                    serverLevel
            ).isPresent()) {
                mask |= directionMask(direction);
            }
        }

        boolean hasMeaningfulInputs =
                !this.inputSlots.getItem(WEAPON_SLOT).isEmpty()
                        && !this.inputSlots.getItem(TEMPLATE_SLOT).isEmpty();
        this.availableDirectionMask.set(
                mask == 0 && !hasMeaningfulInputs ? allDirectionMask() : mask
        );
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        if (container == this.inputSlots) {
            this.createResult();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= AFFIX_BUTTON_OFFSET) {
            // TODO: Support explicit affix targeting after direction/ingredient relation is designed.
            return false;
        }

        TemperingDirection direction = TemperingDirection.byId(buttonId);

        this.selectedDirection.set(direction.id());
        this.createResult();

        return true;
    }

    private ItemStack finalizeTakenResult(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }

        ObeliskTemperingRecipeInput input = this.createRecipeInput();
        Optional<RecipeHolder<ObeliskTemperingRecipe>> foundRecipe =
                serverLevel.recipeAccess().getRecipeFor(
                        ModRecipeTypes.OBELISK_TEMPERING.get(),
                        input,
                        serverLevel
                );

        if (foundRecipe.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return this.createFinalResult(
                player,
                foundRecipe.get().value(),
                input
        );
    }

    private ItemStack createFinalResult(
            Player player,
            ObeliskTemperingRecipe recipe,
            ObeliskTemperingRecipeInput input
    ) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }

        if (!TemperingTemplateItems.hasTemplateData(input.template())) {
            return ItemStack.EMPTY;
        }

        TemperingTemplateData templateData =
                TemperingTemplateItems.getOrDefault(input.template());
        int rolls = recipe.computeRolls(templateData, serverLevel.getRandom());
        ItemStack result = recipe.assembleWithRolls(input, rolls);

        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ObeliskTemperingRoller.resolvePendingRoll(result);
        return result;
    }

    private void onTake(Player player, ItemStack carried) {
        ItemStack finalResult = this.finalizeTakenResult(player);

        if (finalResult.isEmpty()
                || !this.copyFinalResultIntoTakenStack(carried, finalResult)) {
            carried.setCount(0);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.createResult();
            return;
        }

        this.consumeInputsAndAward(player, carried);
    }

    private boolean copyFinalResultIntoTakenStack(
            ItemStack carried,
            ItemStack finalResult
    ) {
        if (carried.isEmpty() || finalResult.isEmpty()) {
            return false;
        }

        if (carried.getItem() != finalResult.getItem()) {
            return false;
        }

        carried.setCount(finalResult.getCount());
        carried.applyComponents(finalResult.getComponentsPatch());
        carried.remove(ModDataComponents.PENDING_TEMPER_ROLL.get());
        return true;
    }

    private void consumeInputsAndAward(Player player, ItemStack crafted) {
        this.resultSlots.awardUsedRecipes(player, this.getRelevantItems());

        if (!crafted.isEmpty()) {
            crafted.onCraftedBy(player, crafted.getCount());
        }

        this.shrinkStackInSlot(WEAPON_SLOT);
        this.shrinkStackInSlot(TEMPLATE_SLOT);

        if (!this.inputSlots.getItem(INGREDIENT_SLOT).isEmpty()) {
            this.shrinkStackInSlot(INGREDIENT_SLOT);
        }

        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, pos) -> level.levelEvent(1044, pos, 0));
        this.createResult();
    }

    private List<ItemStack> getRelevantItems() {
        return List.of(
                this.inputSlots.getItem(WEAPON_SLOT).copy(),
                this.inputSlots.getItem(TEMPLATE_SLOT).copy(),
                this.inputSlots.getItem(INGREDIENT_SLOT).copy()
        );
    }

    private void shrinkStackInSlot(int slot) {
        if (!this.inputSlots.getItem(slot).isEmpty()) {
            this.inputSlots.removeItem(slot, 1);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex == RESULT_SLOT) {
            ItemStack finalResult = this.finalizeTakenResult(player);

            if (finalResult.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack moving = finalResult.copy();

            if (!this.moveItemStackTo(
                    moving,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_END,
                    true
            ) || !moving.isEmpty()) {
                return ItemStack.EMPTY;
            }

            slot.setByPlayer(ItemStack.EMPTY);
            this.consumeInputsAndAward(player, finalResult);
            return finalResult;
        }

        if (slotIndex >= WEAPON_SLOT && slotIndex <= INGREDIENT_SLOT) {
            if (!this.moveItemStackTo(
                    stack,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_END,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= PLAYER_INVENTORY_START
                && slotIndex < PLAYER_HOTBAR_END) {
            if (!this.movePlayerStackToInput(stack)) {
                if (slotIndex < PLAYER_INVENTORY_END) {
                    if (!this.moveItemStackTo(
                            stack,
                            PLAYER_INVENTORY_END,
                            PLAYER_HOTBAR_END,
                            false
                    )) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(
                        stack,
                        PLAYER_INVENTORY_START,
                        PLAYER_INVENTORY_END,
                        false
                )) {
                    return ItemStack.EMPTY;
                }
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    private boolean movePlayerStackToInput(ItemStack stack) {
        if (this.isPotentialWeaponStack(stack)
                && this.moveToEmptyInputSlot(stack, WEAPON_SLOT)) {
            return true;
        }

        if (this.isTemplateStack(stack)
                && this.moveToEmptyInputSlot(stack, TEMPLATE_SLOT)) {
            return true;
        }

        return this.moveToEmptyInputSlot(stack, INGREDIENT_SLOT);
    }

    private boolean moveToEmptyInputSlot(ItemStack stack, int slotIndex) {
        Slot target = this.slots.get(slotIndex);

        if (target.hasItem()) {
            return false;
        }

        return this.moveItemStackTo(stack, slotIndex, slotIndex + 1, false);
    }

    private boolean isTemplateStack(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() == ModItems.TEMPERING_SMITHING_TEMPLATE.get()
                || TemperingTemplateItems.hasTemplateData(stack));
    }

    private boolean isPotentialWeaponStack(ItemStack stack) {
        return !stack.isEmpty()
                && ObeliskTemperingRoller.canTemper(stack, true);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                this.access,
                player,
                ModBlocks.OBELISK_SMITHING_TABLE.get()
        );
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!this.level.isClientSide()) {
            this.access.execute(
                    (level, pos) -> this.clearContainer(player, this.inputSlots)
            );
        }
    }

    public TemperingDirection selectedDirection() {
        return TemperingDirection.byId(this.selectedDirection.get());
    }

    public boolean hasRecipeError() {
        return this.hasRecipeError.get() > 0;
    }

    public boolean hasValidRecipe() {
        return this.hasValidRecipe.get() > 0;
    }

    public int currentPoolHash() {
        return this.currentPoolHash.get();
    }

    public boolean isDirectionAvailable(TemperingDirection direction) {
        return (this.availableDirectionMask.get() & directionMask(direction)) != 0;
    }

    private static int directionMask(TemperingDirection direction) {
        return 1 << direction.id();
    }

    private static int allDirectionMask() {
        int mask = 0;

        for (TemperingDirection direction : TemperingDirection.values()) {
            mask |= directionMask(direction);
        }

        return mask;
    }

    private final class ObeliskTemperingResultSlot extends Slot {
        private ObeliskTemperingResultSlot(
                Container container,
                int slot,
                int x,
                int y
        ) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return ObeliskTemperingMenu.this.hasValidRecipe()
                    && !ObeliskTemperingMenu.this.resultSlots.getItem(0).isEmpty();
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ObeliskTemperingMenu.this.onTake(player, carried);
            super.onTake(player, carried);
        }
    }
}
