package routedpipes.domain.item;

public record ItemBatch(ItemType type, int count) {
    public ItemBatch {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
    }
}
