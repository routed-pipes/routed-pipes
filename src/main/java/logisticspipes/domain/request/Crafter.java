package logisticspipes.domain.request;

import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.routing.RouterId;

// a crafting pipe as the planner sees it, ingredients go to the pipe itself or to its satellite
public record Crafter(RouterId router, Recipe recipe, RouterId ingredientTarget) {
}
