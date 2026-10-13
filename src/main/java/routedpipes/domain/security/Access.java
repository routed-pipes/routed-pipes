package routedpipes.domain.security;

// the answer of a security station, without energy it cannot look anyone up and lets nobody in
public enum Access {
    ALLOWED, DENIED, NO_ENERGY;

    public boolean allowed() {
        return this == ALLOWED;
    }
}
