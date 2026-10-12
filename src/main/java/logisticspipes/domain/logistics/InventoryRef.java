package logisticspipes.domain.logistics;

// names one inventory in the world, so two pipes can tell that they sit on the same one
public record InventoryRef(long id) {
}
