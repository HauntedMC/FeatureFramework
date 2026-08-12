package nl.hauntedmc.featureframework.paper.ui.hud.actionbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ActionBarsTest {

    @Test
    void publicationIsOwnedFailFastAndIdentitySafe() {
        ActionBarService service = mock(ActionBarService.class);
        ActionBarService other = mock(ActionBarService.class);

        assertThrows(IllegalStateException.class, ActionBars::service);
        ActionBars.bootstrap(service);
        assertSame(service, ActionBars.service());
        assertThrows(IllegalStateException.class, () -> ActionBars.bootstrap(other));
        assertThrows(IllegalStateException.class, () -> ActionBars.unpublish(other));
        assertSame(service, ActionBars.service());

        ActionBars.unpublish(service);
        assertThrows(IllegalStateException.class, ActionBars::service);
    }
}
