package logisticspipes.adapter.neoforge.gametest;

import java.util.OptionalInt;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

// a player without a network connection that still opens menus for real
// the vanilla mock server player breaks as soon as another mod sends it a packet
final class TestPlayer extends Player {

    private TestPlayer(GameTestHelper helper) {
        super(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), 0, new GameProfile(UUID.randomUUID(), "test-player"));
    }

    static TestPlayer in(GameTestHelper helper) {
        return new TestPlayer(helper);
    }

    @Override
    public OptionalInt openMenu(MenuProvider provider) {
        containerMenu = provider.createMenu(1, getInventory(), this);
        return OptionalInt.of(1);
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }
}
