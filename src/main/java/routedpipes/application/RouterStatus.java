package routedpipes.application;

// what a router sees of its network: how many other routers it can reach and how much energy they share
public record RouterStatus(int routers, int energy) {
}
