package logisticspipes.domain.request;

import java.util.Map;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// what one provider can hand out right now
public record Stock(RouterId provider, Map<ItemType, Integer> items) {
}
