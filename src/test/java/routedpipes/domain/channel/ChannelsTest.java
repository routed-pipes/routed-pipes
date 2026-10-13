package routedpipes.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChannelsTest {

    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID FRIEND = new UUID(0, 2);
    private static final UUID STRANGER = new UUID(0, 3);
    private static final UUID STATION = new UUID(9, 1);
    private static final StationAccess NO_STATIONS = (station, player) -> false;
    // the station lets the friend use its channels
    private static final StationAccess STATIONS = (station, player) -> station.equals(STATION) && player.equals(FRIEND);

    private final Channels channels = new Channels();

    private Channel create(String name, ChannelAccess access, UUID station) {
        return channels.create(name, OWNER, "Owner", access, station).orElseThrow();
    }

    @Test
    void aPublicChannelIsForEveryone() {
        Channel channel = create("ores", ChannelAccess.PUBLIC, null);

        assertThat(channels.allowedFor(STRANGER, NO_STATIONS)).containsExactly(channel);
    }

    @Test
    void aPrivateChannelIsForItsOwnerOnly() {
        Channel channel = create("ores", ChannelAccess.PRIVATE, null);

        assertThat(channels.allowedFor(OWNER, NO_STATIONS)).containsExactly(channel);
        assertThat(channels.allowedFor(FRIEND, STATIONS)).isEmpty();
    }

    @Test
    void aSecuredChannelIsForWhoeverItsStationLets() {
        Channel channel = create("ores", ChannelAccess.SECURED, STATION);

        assertThat(channel.station()).isEqualTo(STATION);
        assertThat(channels.allowedFor(FRIEND, STATIONS)).containsExactly(channel);
        assertThat(channels.allowedFor(STRANGER, STATIONS)).isEmpty();
    }

    @Test
    void aSecuredChannelWhoseStationIsGoneIsAsGoodAsPrivate() {
        Channel channel = create("ores", ChannelAccess.SECURED, STATION);

        assertThat(channels.allowedFor(FRIEND, NO_STATIONS)).isEmpty();
        assertThat(channels.allowedFor(OWNER, NO_STATIONS)).containsExactly(channel);
    }

    @Test
    void withoutAStationAChannelCannotBeSecured() {
        Channel channel = create("ores", ChannelAccess.SECURED, null);

        assertThat(channel.access()).isEqualTo(ChannelAccess.PRIVATE);
        assertThat(channels.setAccess(channel.id(), OWNER, ChannelAccess.SECURED, null)).isFalse();
    }

    @Test
    void namesAreTrimmedAndBlankOnesRefused() {
        assertThat(channels.create("   ", OWNER, "Owner", ChannelAccess.PUBLIC, null)).isEmpty();
        assertThat(create("  ores ", ChannelAccess.PUBLIC, null).name()).isEqualTo("ores");
        assertThat(create("x".repeat(100), ChannelAccess.PUBLIC, null).name()).hasSize(Channels.MAX_NAME);
        assertThat(channels.all()).hasSize(2);
    }

    @Test
    void onlyTheOwnerRenamesAChannel() {
        Channel channel = create("ores", ChannelAccess.PUBLIC, null);

        assertThat(channels.rename(channel.id(), STRANGER, "mine")).isFalse();
        assertThat(channels.rename(channel.id(), OWNER, " ")).isFalse();
        assertThat(channels.rename(channel.id(), OWNER, "ingots")).isTrue();

        assertThat(channels.find(channel.id()).orElseThrow().name()).isEqualTo("ingots");
    }

    @Test
    void onlyTheOwnerChangesWhoMayUseAChannel() {
        Channel channel = create("ores", ChannelAccess.PUBLIC, null);

        assertThat(channels.setAccess(channel.id(), STRANGER, ChannelAccess.PRIVATE, null)).isFalse();
        assertThat(channels.setAccess(channel.id(), OWNER, ChannelAccess.SECURED, STATION)).isTrue();
        assertThat(channels.find(channel.id()).orElseThrow().station()).isEqualTo(STATION);
        assertThat(channels.setAccess(channel.id(), OWNER, ChannelAccess.PRIVATE, STATION)).isTrue();

        Channel changed = channels.find(channel.id()).orElseThrow();
        assertThat(changed.access()).isEqualTo(ChannelAccess.PRIVATE);
        assertThat(changed.station()).isNull();
    }

    @Test
    void onlyTheOwnerDeletesAChannel() {
        Channel channel = create("ores", ChannelAccess.PUBLIC, null);

        assertThat(channels.delete(channel.id(), STRANGER)).isFalse();
        assertThat(channels.find(channel.id())).isPresent();
        assertThat(channels.delete(channel.id(), OWNER)).isTrue();
        assertThat(channels.find(channel.id())).isEmpty();
    }

    @Test
    void aPlayerCannotMakeEndlessChannels() {
        Channels channels = new Channels();
        java.util.UUID owner = java.util.UUID.randomUUID();
        for (int index = 0; index < Channels.MAX_PER_OWNER; index++) {
            assertThat(channels.create("c" + index, owner, "Owner", ChannelAccess.PRIVATE, null)).isPresent();
        }

        assertThat(channels.create("one more", owner, "Owner", ChannelAccess.PRIVATE, null)).isEmpty();
        assertThat(channels.create("someone else's", java.util.UUID.randomUUID(), "Other", ChannelAccess.PRIVATE, null)).isPresent();
    }
}
