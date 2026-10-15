package routedpipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Map;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.domain.item.ItemType;
import net.minecraft.world.item.ItemStack;

// a recipe viewer slot that takes any of several items, like any planks: the one there is most of wins,
// one that can only be crafted beats one that is nowhere, and with none of them the first one stays
final class MarkerChoice {

    private MarkerChoice() {
    }

    static ItemStack pick(List<ItemStack> choices, Map<ItemType, Integer> available) {
        ItemStack best = choices.get(0);
        int most = -1;
        for (ItemStack choice : choices) {
            Integer count = choice.isEmpty() ? null : available.get(McItemType.batchOf(choice).type());
            if (count != null && count > most) {
                best = choice;
                most = count;
            }
        }
        return best.copyWithCount(choices.get(0).getCount());
    }
}
