package nl.hauntedmc.featureframework.testutil;

import org.mockito.MockedConstruction;
import org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker;
import org.mockito.internal.creation.bytebuddy.InlineByteBuddyMockMaker;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;

import java.util.Optional;
import java.util.function.Function;

/**
 * Uses subclass-based mocks for ordinary mocking and inline only for static/construction mocks.
 *
 * This avoids class retransformation for common interface/class mocks under coverage agents
 * while keeping support for tests that rely on static mocking.
 *
 * Mockito's {@link MockMaker} SPI still exposes raw handler/settings parameters, so implementations
 * must mirror those raw signatures to override the interface methods.
 */
@SuppressWarnings("rawtypes")
public final class CoverageFriendlyMockMaker implements MockMaker {

    private final MockMaker subclass = new ByteBuddyMockMaker();
    private final MockMaker inline = new InlineByteBuddyMockMaker();

    @Override
    public <T> T createMock(MockCreationSettings<T> settings, MockHandler handler) {
        Class<T> type = settings.getTypeToMock();
        if (isSubclassMockable(type)) {
            return subclass.createMock(settings, handler);
        }
        return inline.createMock(settings, handler);
    }

    @Override
    public <T> Optional<T> createSpy(MockCreationSettings<T> settings, MockHandler handler, T instance) {
        Class<T> type = settings.getTypeToMock();
        if (isSubclassMockable(type)) {
            return subclass.createSpy(settings, handler, instance);
        }
        return inline.createSpy(settings, handler, instance);
    }

    @Override
    public MockHandler getHandler(Object mock) {
        MockHandler handler = subclass.getHandler(mock);
        return handler != null ? handler : inline.getHandler(mock);
    }

    @Override
    public void resetMock(Object mock, MockHandler newHandler, MockCreationSettings settings) {
        MockHandler existing = subclass.getHandler(mock);
        if (existing != null) {
            subclass.resetMock(mock, newHandler, settings);
            return;
        }
        inline.resetMock(mock, newHandler, settings);
    }

    @Override
    public TypeMockability isTypeMockable(Class<?> type) {
        TypeMockability subclassMockability = subclass.isTypeMockable(type);
        if (subclassMockability.mockable()) {
            return subclassMockability;
        }
        return inline.isTypeMockable(type);
    }

    @Override
    public <T> StaticMockControl<T> createStaticMock(
            Class<T> type,
            MockCreationSettings<T> settings,
            MockHandler handler
    ) {
        return inline.createStaticMock(type, settings, handler);
    }

    @Override
    public <T> ConstructionMockControl<T> createConstructionMock(
            Class<T> type,
            Function<MockedConstruction.Context, MockCreationSettings<T>> settingsFactory,
            Function<MockedConstruction.Context, MockHandler<T>> handlerFactory,
            MockedConstruction.MockInitializer<T> initializer
    ) {
        return inline.createConstructionMock(type, settingsFactory, handlerFactory, initializer);
    }

    @Override
    public void clearAllCaches() {
        subclass.clearAllCaches();
        inline.clearAllCaches();
    }

    private boolean isSubclassMockable(Class<?> type) {
        return subclass.isTypeMockable(type).mockable();
    }
}
