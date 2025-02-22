package edivad.extrastorage.nodes;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.network.node.ConnectivityStateChangeCause;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import com.refinedmods.refinedstorage.inventory.item.BaseItemHandler;
import com.refinedmods.refinedstorage.inventory.item.UpgradeItemHandler;
import com.refinedmods.refinedstorage.inventory.item.validator.PatternItemValidator;
import com.refinedmods.refinedstorage.inventory.listener.NetworkNodeInventoryListener;
import com.refinedmods.refinedstorage.item.UpgradeItem;
import com.refinedmods.refinedstorage.util.LevelUtils;
import com.refinedmods.refinedstorage.util.StackUtils;
import edivad.extrastorage.Main;
import edivad.extrastorage.blocks.CrafterTier;
import edivad.extrastorage.setup.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AdvancedCrafterNetworkNode extends NetworkNode implements ICraftingPatternContainer {
    private static final String NBT_DISPLAY_NAME = "DisplayName";
    private static final String NBT_UUID = "CrafterUuid";
    private static final String NBT_MODE = "Mode";
    private static final String NBT_LOCKED = "Locked";
    private static final String NBT_WAS_POWERED = "WasPowered";
    private static final String NBT_TIER = "Tier";
    private final Component DEFAULT_NAME;
    private final BaseItemHandler patternsInventory;
    private final Map<Integer, ICraftingPattern> slot_to_pattern = new HashMap<>();
    private final List<ICraftingPattern> patterns = new ArrayList<>();
    private final UpgradeItemHandler upgrades = (UpgradeItemHandler) new UpgradeItemHandler(4, UpgradeItem.Type.SPEED)
            .addListener(new NetworkNodeInventoryListener(this));
    private final ResourceLocation ID;
    // Used to prevent infinite recursion on getRootContainer() when there's e.g. two crafters facing each other.
    private boolean visited = false;

    private CrafterMode mode = CrafterMode.IGNORE;
    private boolean locked = false;
    private boolean wasPowered;

    @Nullable
    private Component displayName;

    @Nullable
    private UUID uuid = null;

    private CrafterTier tier;
    public AdvancedCrafterNetworkNode(Level level, BlockPos pos, CrafterTier tier) {
        super(level, pos);
        this.tier = tier;
        this.patternsInventory = new BaseItemHandler(this.tier.getSlots()) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!stacks.get(slot).isEmpty()) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }
        }
        .addValidator(new PatternItemValidator(level))
        .addListener(new NetworkNodeInventoryListener(this))
        .addListener((handler, slot, reading) -> {
            if (!reading) {
                if (!level.isClientSide)
                    invalidateSlot(slot);
                if (network != null)
                    network.getCraftingManager().invalidate();
            }
        });
        DEFAULT_NAME = Component.translatable("block." + Main.MODID + "." + this.tier.getID());
        ID = new ResourceLocation(Main.MODID, tier.getID());
    }

    private void invalidate() {
        slot_to_pattern.clear();
        patterns.clear();

        for (int i = 0; i < patternsInventory.getSlots(); ++i) {
            invalidateSlot(i);
        }
    }

    private void invalidateSlot(int slot) {
        if (slot_to_pattern.containsKey(slot)) {
            patterns.remove(slot_to_pattern.remove(slot));
        }

        var patternStack = patternsInventory.getStackInSlot(slot);

        if (!patternStack.isEmpty()) {
            var pattern = ((ICraftingPatternProvider) patternStack.getItem()).create(level, patternStack, this);

            if (pattern.isValid()) {
                slot_to_pattern.put(slot, pattern);
                patterns.add(pattern);
            }
        }
    }

    @Override
    public int getEnergyUsage() {
        int energyPatterns = Config.AdvancedCrafter.INCLUDE_PATTERN_ENERGY.get() ? 2 * patterns.size() : 0;
        int energyCrafter = Config.AdvancedCrafter.BASE_ENERGY.get() * (tier.ordinal() + 1);
        return energyCrafter + upgrades.getEnergyUsage() + energyPatterns;
    }

    @Override
    public void update() {
        super.update();

        if (ticks == 1)
            invalidate();

        if (mode == CrafterMode.PULSE_INSERTS_NEXT_SET && level.isLoaded(pos)) {
            if (level.hasNeighborSignal(pos)) {
                this.wasPowered = true;
                markDirty();
            } else if (wasPowered) {
                this.wasPowered = false;
                this.locked = false;
                markDirty();
            }
        }
    }

    @Override
    protected void onConnectedStateChange(INetwork network, boolean state, ConnectivityStateChangeCause cause) {
        super.onConnectedStateChange(network, state, cause);
        network.getCraftingManager().invalidate();
    }

    @Override
    public void onDisconnected(INetwork network) {
        super.onDisconnected(network);
        network.getCraftingManager().getTasks().stream()
                .filter(task -> task.getPattern().getContainer().getPosition().equals(pos))
                .forEach(task -> network.getCraftingManager().cancel(task.getId()));
    }

    @Override
    public void onDirectionChanged(Direction direction) {
        super.onDirectionChanged(direction);
        if (network != null)
            network.getCraftingManager().invalidate();
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        StackUtils.readItems(patternsInventory, 0, tag);
        invalidate();
        StackUtils.readItems(upgrades, 1, tag);
        if (tag.contains(NBT_DISPLAY_NAME)) {
            displayName = Component.Serializer.fromJson(tag.getString(NBT_DISPLAY_NAME));
        }
        if (tag.hasUUID(NBT_UUID)) {
            uuid = tag.getUUID(NBT_UUID);
        }
        if (tag.contains(NBT_MODE)) {
            mode = CrafterMode.getById(tag.getInt(NBT_MODE));
        }
        if (tag.contains(NBT_LOCKED)) {
            locked = tag.getBoolean(NBT_LOCKED);
        }
        if (tag.contains(NBT_WAS_POWERED)) {
            wasPowered = tag.getBoolean(NBT_WAS_POWERED);
        }
        if (tag.contains(NBT_TIER)) {
            tier = CrafterTier.values()[tag.getInt(NBT_TIER)];
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public CompoundTag write(CompoundTag tag) {
        super.write(tag);
        StackUtils.writeItems(patternsInventory, 0, tag);
        StackUtils.writeItems(upgrades, 1, tag);
        if (displayName != null) {
            tag.putString(NBT_DISPLAY_NAME, Component.Serializer.toJson(displayName));
        }
        if (uuid != null) {
            tag.putUUID(NBT_UUID, uuid);
        }
        tag.putInt(NBT_MODE, mode.ordinal());
        tag.putBoolean(NBT_LOCKED, locked);
        tag.putBoolean(NBT_WAS_POWERED, wasPowered);
        tag.putInt(NBT_TIER, tier.ordinal());
        return tag;
    }

    @Override
    public int getUpdateInterval() {
        int upgradesCount = upgrades.getUpgradeCount(UpgradeItem.Type.SPEED);
        if (upgradesCount < 0 || upgradesCount > 4)
            return 0;
        else
            return 10 - (upgradesCount * 2);//Min:2 Max:10
    }

    @Override
    public int getMaximumSuccessfulCraftingUpdates() {
        int speed = getTierSpeed();
        if (hasConnectedInventory())
            return Math.min(speed, getConnectedInventory().getSlots());
        return speed;
    }

    public int getTierSpeed() {
        int upgradesCount = upgrades.getUpgradeCount(UpgradeItem.Type.SPEED);
        if (tier.equals(CrafterTier.IRON))
            return upgradesCount + tier.getCraftingSpeed();
        return (upgradesCount * (tier.getCraftingSpeed() / 5)) + tier.getCraftingSpeed();//PREV Min:1 Max:5
    }

    @Nullable
    @Override
    public IItemHandler getConnectedInventory() {
        var proxy = getRootContainer();
        if (proxy == null)
            return null;
        return LevelUtils.getItemHandler(proxy.getFacingBlockEntity(), proxy.getDirection().getOpposite());
    }

    @Nullable
    @Override
    public IFluidHandler getConnectedFluidInventory() {
        var proxy = getRootContainer();
        if (proxy == null)
            return null;
        return LevelUtils.getFluidHandler(proxy.getFacingBlockEntity(), proxy.getDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity getConnectedBlockEntity() {
        var proxy = getRootContainer();
        if (proxy == null)
            return null;
        return proxy.getFacingBlockEntity();
    }

    @Override
    public List<ICraftingPattern> getPatterns() {
        return patterns;
    }

    @Nullable
    @Override
    public IItemHandlerModifiable getPatternInventory() {
        return patternsInventory;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return displayName;
    }

    @Override
    public Component getName() {
        if (displayName != null)
            return displayName;
        ICraftingPatternContainer root = getRootContainer();
        if (root != null) {
            Component displayNameOfRoot = root.getCustomName();
            if (displayNameOfRoot != null) {
                return displayNameOfRoot;
            }
        }
        var facing = getConnectedBlockEntity();
        if (facing instanceof Nameable face && face.getName() != null)
            return face.getName();
        if (facing != null)
            return Component.translatable(level.getBlockState(facing.getBlockPos()).getBlock().getDescriptionId());
        return DEFAULT_NAME;
    }

    @Nullable
    public Component getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Component displayName) {
        this.displayName = displayName;
    }

    @Override
    public BlockPos getPosition() {
        return pos;
    }

    public CrafterMode getMode() {
        return mode;
    }

    public void setMode(CrafterMode mode) {
        this.mode = mode;
        this.wasPowered = false;
        this.locked = false;

        markDirty();
    }

    public IItemHandler getPatternItems() {
        return patternsInventory;
    }

    public UpgradeItemHandler getUpgrades() {
        return upgrades;
    }

    @Nullable
    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(patternsInventory, upgrades);
    }

    @Nullable
    @Override
    public ICraftingPatternContainer getRootContainer() {
        if (visited)
            return null;

        INetworkNode facing = API.instance().getNetworkNodeManager((ServerLevel) level).getNode(pos.relative(getDirection()));
        if (!(facing instanceof ICraftingPatternContainer container) || facing.getNetwork() != network)
            return this;

        visited = true;
        var facingContainer = container.getRootContainer();
        visited = false;

        return facingContainer;
    }

    public Optional<ICraftingPatternContainer> getRootContainerNotSelf() {
        var root = getRootContainer();
        if (root != null && root != this)
            return Optional.of(root);
        return Optional.empty();
    }

    @Override
    public UUID getUuid() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
            markDirty();
        }
        return this.uuid;
    }

    @Override
    public boolean isLocked() {
        return getRootContainerNotSelf()
                .map(ICraftingPatternContainer::isLocked)
                .orElseGet(() -> switch (mode) {
                    case SIGNAL_LOCKS_AUTOCRAFTING -> level.hasNeighborSignal(pos);
                    case SIGNAL_UNLOCKS_AUTOCRAFTING -> !level.hasNeighborSignal(pos);
                    case PULSE_INSERTS_NEXT_SET -> locked;
                    default -> false;
                });
    }

    @Override
    public void unlock() {
        locked = false;
    }

    @Override
    public void onUsedForProcessing() {
        var root = getRootContainerNotSelf();
        if (root.isPresent()) {
            root.get().onUsedForProcessing();
        } else if (mode == CrafterMode.PULSE_INSERTS_NEXT_SET) {
            this.locked = true;
            markDirty();
        }
    }

    public enum CrafterMode {
        IGNORE,
        SIGNAL_UNLOCKS_AUTOCRAFTING,
        SIGNAL_LOCKS_AUTOCRAFTING,
        PULSE_INSERTS_NEXT_SET;

        public static CrafterMode getById(int id) {
            if (id >= 0 && id < values().length)
                return values()[id];
            return IGNORE;
        }
    }
}
