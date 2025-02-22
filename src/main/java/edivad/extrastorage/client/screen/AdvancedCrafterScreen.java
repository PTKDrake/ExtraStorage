package edivad.extrastorage.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.refinedmods.refinedstorage.screen.BaseScreen;
import com.refinedmods.refinedstorage.util.RenderUtils;
import edivad.extrastorage.Main;
import edivad.extrastorage.blocks.CrafterTier;
import edivad.extrastorage.container.AdvancedCrafterContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AdvancedCrafterScreen extends BaseScreen<AdvancedCrafterContainerMenu> {
    private final CrafterTier tier;

    public AdvancedCrafterScreen(AdvancedCrafterContainerMenu container, Inventory inventory, Component title) {
        super(container, 211, 173 + (container.getBlockEntity().getTier().ordinal() * 36), inventory, title);
        this.tier = container.getBlockEntity().getTier();
    }

    @Override
    public void onPostInit(int i, int i1) {
    }

    @Override
    public void tick(int i, int i1) {
    }

    @Override
    public void renderBackground(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        bindTexture(Main.MODID, "gui/" + tier.getID() + ".png");
        if (imageHeight <= 256)
            blit(poseStack, x, y, 0, 0, imageWidth, imageHeight);
        else
            blit(poseStack, x, y, 0, 0, imageWidth, imageHeight, 512, 512);
    }

    @Override
    public void renderForeground(PoseStack poseStack, int mouseX, int mouseY) {
        renderString(poseStack, 7, 7, RenderUtils.shorten(title.getString(), 26));
        renderString(poseStack, 7, 78 + (36 * tier.ordinal()), Component.translatable("container.inventory").getString());
    }
}
