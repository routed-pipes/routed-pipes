package routedpipes.domain.channel;

// who may put a connector on a channel
public enum ChannelAccess {
    // everyone
    PUBLIC,
    // whoever the security station of the channel lets, and the owner
    SECURED,
    // only the owner
    PRIVATE
}
