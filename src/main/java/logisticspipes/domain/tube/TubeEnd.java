package logisticspipes.domain.tube;

import logisticspipes.domain.routing.Side;

// an opening of a tube: the block it is in and the side of that block it opens to
public record TubeEnd(Offset block, Side side) {
}
