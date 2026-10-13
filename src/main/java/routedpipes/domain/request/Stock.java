package routedpipes.domain.request;

import java.util.Map;

import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;

// what one provider can hand out right now
public record Stock(RouterId provider, Map<ItemType, Integer> items) {
}
