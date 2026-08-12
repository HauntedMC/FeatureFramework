package nl.hauntedmc.featureframework.service;

/** Publication boundary for a service registry that enforces provider ownership. */
public interface OwnedServiceRegistry<O> {

    <T> Registration register(O owner, Class<T> type, T instance);

    <T> Registration replace(O owner, Class<T> type, T instance);
}
