package logisticspipes.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SecuritySettingsTest {

    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID FRIEND = new UUID(0, 2);
    private static final UUID STRANGER = new UUID(0, 3);

    private final SecuritySettings settings = new SecuritySettings();
    private int energy = 1000;
    private final EnergySource source = amount -> {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    };

    @Test
    void theOwnerMayDoEverything() {
        settings.setOwner(OWNER);

        for (Permission permission : Permission.values()) {
            assertThat(settings.check(OWNER, permission, source)).isEqualTo(Access.ALLOWED);
        }
    }

    @Test
    void theOwnerNeedsNoEnergy() {
        settings.setOwner(OWNER);
        energy = 0;

        assertThat(settings.check(OWNER, Permission.REMOVE_PIPES, source)).isEqualTo(Access.ALLOWED);
    }

    @Test
    void checkingTheOwnerCostsNothing() {
        settings.setOwner(OWNER);

        settings.check(OWNER, Permission.REMOVE_PIPES, source);

        assertThat(energy).isEqualTo(1000);
    }

    @Test
    void everyoneElseStartsWithNothingAllowed() {
        settings.setOwner(OWNER);
        settings.add(FRIEND);

        for (Permission permission : Permission.values()) {
            assertThat(settings.check(FRIEND, permission, source)).isEqualTo(Access.DENIED);
            assertThat(settings.check(STRANGER, permission, source)).isEqualTo(Access.DENIED);
        }
    }

    @Test
    void aPlayerMayDoWhatWasSwitchedOnForThemAndNothingElse() {
        settings.set(FRIEND, Permission.ACTIVE_REQUESTING, true);

        assertThat(settings.check(FRIEND, Permission.ACTIVE_REQUESTING, source)).isEqualTo(Access.ALLOWED);
        assertThat(settings.check(FRIEND, Permission.CONFIGURE_SETTINGS, source)).isEqualTo(Access.DENIED);
        assertThat(settings.check(STRANGER, Permission.ACTIVE_REQUESTING, source)).isEqualTo(Access.DENIED);
    }

    @Test
    void aPermissionCanBeTakenAwayAgain() {
        settings.set(FRIEND, Permission.REMOVE_PIPES, true);
        settings.set(FRIEND, Permission.REMOVE_PIPES, false);

        assertThat(settings.check(FRIEND, Permission.REMOVE_PIPES, source)).isEqualTo(Access.DENIED);
        assertThat(settings.players()).containsExactly(FRIEND);
    }

    @Test
    void lookingSomeoneUpCostsEnergyWhateverTheAnswer() {
        settings.set(FRIEND, Permission.CHECK_NETWORK, true);

        settings.check(FRIEND, Permission.CHECK_NETWORK, source);
        settings.check(STRANGER, Permission.CHECK_NETWORK, source);

        assertThat(energy).isEqualTo(1000 - 2 * SecuritySettings.CHECK_ENERGY);
    }

    @Test
    void withoutEnergyNobodyButTheOwnerGetsIn() {
        settings.set(FRIEND, Permission.CHECK_NETWORK, true);
        energy = SecuritySettings.CHECK_ENERGY - 1;

        assertThat(settings.check(FRIEND, Permission.CHECK_NETWORK, source)).isEqualTo(Access.NO_ENERGY);
        assertThat(energy).isEqualTo(SecuritySettings.CHECK_ENERGY - 1);
    }

    @Test
    void aStationNobodyClaimedHasNoOwner() {
        assertThat(settings.owner()).isEmpty();
        assertThat(settings.isOwner(STRANGER)).isFalse();
        assertThat(settings.check(STRANGER, Permission.CHECK_NETWORK, source)).isEqualTo(Access.DENIED);
    }

    @Test
    void playersAreListedInTheOrderTheyWereAdded() {
        settings.add(STRANGER);
        settings.add(FRIEND);
        settings.add(STRANGER);

        assertThat(settings.players()).containsExactly(STRANGER, FRIEND);
        assertThat(settings.permissions(STRANGER)).isEmpty();
    }

    @Test
    void automatedRemovalIsOffUntilSwitchedOn() {
        assertThat(settings.checkAutomatedRemoval(source)).isEqualTo(Access.DENIED);

        settings.setAutomatedRemoval(true);

        assertThat(settings.checkAutomatedRemoval(source)).isEqualTo(Access.ALLOWED);
    }

    @Test
    void automatedRemovalNeedsEnergyToBeAllowed() {
        settings.setAutomatedRemoval(true);
        energy = 0;

        assertThat(settings.checkAutomatedRemoval(source)).isEqualTo(Access.NO_ENERGY);
    }

    @Test
    void aNewStationVouchesForItsCards() {
        assertThat(settings.authorized()).isTrue();

        settings.setAuthorized(false);

        assertThat(settings.authorized()).isFalse();
    }

    @Test
    void computersAreKeptOutUnlessListed() {
        settings.addComputerException(4);

        assertThat(settings.checkComputer(4, source)).isEqualTo(Access.ALLOWED);
        assertThat(settings.checkComputer(5, source)).isEqualTo(Access.DENIED);
        assertThat(settings.checkComputer(-1, source)).isEqualTo(Access.DENIED);
    }

    @Test
    void withComputersAllowedTheListedOnesAreKeptOut() {
        settings.setComputersAllowed(true);
        settings.addComputerException(4);

        assertThat(settings.checkComputer(4, source)).isEqualTo(Access.DENIED);
        assertThat(settings.checkComputer(5, source)).isEqualTo(Access.ALLOWED);
        assertThat(settings.checkComputer(-1, source)).isEqualTo(Access.ALLOWED);
    }

    @Test
    void aComputerTakenOffTheListIsLikeAnyOther() {
        settings.addComputerException(4);
        settings.removeComputerException(4);

        assertThat(settings.checkComputer(4, source)).isEqualTo(Access.DENIED);
        assertThat(settings.computerExceptions()).isEmpty();
    }

    @Test
    void theListOfComputersIsSortedAndHasEveryIdOnce() {
        settings.addComputerException(9);
        settings.addComputerException(-1);
        settings.addComputerException(3);
        settings.addComputerException(9);

        assertThat(settings.computerExceptions()).containsExactly(-1, 3, 9);
    }

    @Test
    void everyLookAtAComputerCostsEnergy() {
        settings.setComputersAllowed(true);

        settings.checkComputer(4, source);

        assertThat(energy).isEqualTo(1000 - SecuritySettings.CHECK_ENERGY);
    }

    @Test
    void withoutEnergyNoComputerGetsIn() {
        settings.setComputersAllowed(true);
        energy = SecuritySettings.CHECK_ENERGY - 1;

        assertThat(settings.checkComputer(4, source)).isEqualTo(Access.NO_ENERGY);
    }
}
