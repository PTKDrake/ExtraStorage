package edivad.extrastorage.datagen;

import edivad.extrastorage.Main;
import edivad.extrastorage.blocks.CrafterTier;
import edivad.extrastorage.items.storage.fluid.FluidStorageType;
import edivad.extrastorage.items.storage.item.ItemStorageType;
import edivad.extrastorage.setup.Registration;
import edivad.extrastorage.tools.Translations;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.LanguageProvider;

public class Lang extends LanguageProvider {
    public Lang(DataGenerator gen) {
        super(gen, Main.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + Main.MODID + "_tab", Main.MODNAME);

        for (ItemStorageType type : ItemStorageType.values()) {
            add(Registration.ITEM_STORAGE_PART.get(type).get(), type.getName() + " Storage Part");
            add(Registration.ITEM_DISK.get(type).get(), type.getName() + " Storage Disk");
            add(Registration.ITEM_STORAGE_BLOCK.get(type).get(), type.getName() + " Storage Block");
        }

        for (FluidStorageType type : FluidStorageType.values()) {
            add(Registration.FLUID_STORAGE_PART.get(type).get(), type.getName() + " Fluid Storage Part");
            add(Registration.FLUID_DISK.get(type).get(), type.getName() + " Fluid Storage Disk");
            add(Registration.FLUID_STORAGE_BLOCK.get(type).get(), type.getName() + " Fluid Storage Block");
        }

        for (CrafterTier tier : CrafterTier.values()) {
            String baseName = tier.name().toLowerCase();
            String TierName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
            add(Registration.CRAFTER.get(tier).get(), TierName + " Crafter");
        }

        add(Registration.ADVANCED_EXPORTER.get(), "Advanced Exporter");
        add(Registration.ADVANCED_IMPORTER.get(), "Advanced Importer");
        add(Registration.RAW_NEURAL_PROCESSOR_ITEM.get(), "Raw Neural Processor");
        add(Registration.NEURAL_PROCESSOR_ITEM.get(), "Neural Processor");
        add(Translations.HOLD_SHIFT, "Hold Shift for details");
        add(Translations.SLOT_CRAFTING, "Slots for patterns: %s");
        add(Translations.BASE_SPEED, "Base speed: %sx");
        add(Translations.CURRENT_SPEED, "Current speed: %sx");
        add(Translations.LIMITED_SPEED, "Current speed (limited by %s): %sx");
        add(Translations.OCCUPIED_SPACE, "Occupied space: %s/%s");

        add(Translations.IRON_CRAFTER.title(), "Iron Crafter");
        add(Translations.IRON_CRAFTER.desc(), "The next generation of crafters!");
        add(Translations.ADVANCED_EXPORTER.title(), "Advanced Exporter");
        add(Translations.ADVANCED_EXPORTER.desc(), "A much larger exporter!");
        add(Translations.ADVANCED_IMPORTER.title(), "Advanced Importer");
        add(Translations.ADVANCED_IMPORTER.desc(), "Many more filters!");
    }
}
