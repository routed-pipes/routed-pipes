package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;

import routedpipes.domain.hud.HudCategory;
import routedpipes.domain.hud.HudElement;
import routedpipes.domain.hud.HudPanel;
import routedpipes.domain.hud.HudText;
import routedpipes.domain.item.ItemBatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

// how a panel travels from the server to the client
public final class HudPanelCodec {

    private static final int LABEL = 0;
    private static final int ENTRY = 1;
    private static final int GRID = 2;
    private static final int BAR = 3;
    private static final int MODULES = 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, HudPanel> CODEC = StreamCodec.of(HudPanelCodec::write, HudPanelCodec::read);

    private HudPanelCodec() {
    }

    private static void write(RegistryFriendlyByteBuf buffer, HudPanel panel) {
        buffer.writeEnum(panel.category());
        buffer.writeVarInt(panel.width());
        buffer.writeVarInt(panel.height());
        writeElements(buffer, panel.elements());
    }

    private static HudPanel read(RegistryFriendlyByteBuf buffer) {
        return new HudPanel(buffer.readEnum(HudCategory.class), buffer.readVarInt(), buffer.readVarInt(), readElements(buffer));
    }

    private static void writeElements(RegistryFriendlyByteBuf buffer, List<HudElement> elements) {
        buffer.writeVarInt(elements.size());
        for (HudElement element : elements) {
            switch (element) {
                case HudElement.Label label -> {
                    buffer.writeByte(LABEL);
                    writeText(buffer, label.text());
                    buffer.writeBoolean(label.centred());
                }
                case HudElement.Entry entry -> {
                    buffer.writeByte(ENTRY);
                    writeText(buffer, entry.text());
                    writeBatch(buffer, entry.stack());
                }
                case HudElement.Grid grid -> {
                    buffer.writeByte(GRID);
                    buffer.writeCollection(grid.stacks(), (out, batch) -> writeBatch(buffer, batch));
                    buffer.writeVarInt(grid.columns());
                    buffer.writeVarInt(grid.perPage());
                }
                case HudElement.Bar bar -> {
                    buffer.writeByte(BAR);
                    buffer.writeVarLong(bar.stored());
                    buffer.writeVarLong(bar.capacity());
                }
                case HudElement.Modules modules -> {
                    buffer.writeByte(MODULES);
                    buffer.writeVarInt(modules.modules().size());
                    for (HudElement.Module module : modules.modules()) {
                        writeBatch(buffer, module.icon());
                        writeElements(buffer, module.content());
                    }
                    writeElements(buffer, modules.unpicked());
                }
            }
        }
    }

    private static List<HudElement> readElements(RegistryFriendlyByteBuf buffer) {
        List<HudElement> elements = new ArrayList<>();
        int size = buffer.readVarInt();
        for (int index = 0; index < size; index++) {
            int kind = buffer.readByte();
            elements.add(switch (kind) {
                case LABEL -> new HudElement.Label(readText(buffer), buffer.readBoolean());
                case ENTRY -> new HudElement.Entry(readText(buffer), readBatch(buffer));
                case GRID -> new HudElement.Grid(buffer.readList(in -> readBatch(buffer)), buffer.readVarInt(), buffer.readVarInt());
                case BAR -> new HudElement.Bar(buffer.readVarLong(), buffer.readVarLong());
                case MODULES -> {
                    List<HudElement.Module> modules = new ArrayList<>();
                    int count = buffer.readVarInt();
                    for (int module = 0; module < count; module++) {
                        modules.add(new HudElement.Module(readBatch(buffer), readElements(buffer)));
                    }
                    yield new HudElement.Modules(modules, readElements(buffer));
                }
                default -> throw new IllegalArgumentException("unknown panel element " + kind);
            });
        }
        return elements;
    }

    private static void writeText(RegistryFriendlyByteBuf buffer, HudText text) {
        buffer.writeUtf(text.key());
        buffer.writeCollection(text.args(), (out, arg) -> out.writeUtf(arg));
    }

    private static HudText readText(RegistryFriendlyByteBuf buffer) {
        return new HudText(buffer.readUtf(), buffer.readList(in -> in.readUtf()));
    }

    // the count goes on its own, it can be more than a stack holds
    private static void writeBatch(RegistryFriendlyByteBuf buffer, ItemBatch batch) {
        ItemStack.STREAM_CODEC.encode(buffer, McItemType.stackOf(new ItemBatch(batch.type(), 1)));
        buffer.writeVarInt(batch.count());
    }

    private static ItemBatch readBatch(RegistryFriendlyByteBuf buffer) {
        return new ItemBatch(McItemType.batchOf(ItemStack.STREAM_CODEC.decode(buffer)).type(), buffer.readVarInt());
    }
}
