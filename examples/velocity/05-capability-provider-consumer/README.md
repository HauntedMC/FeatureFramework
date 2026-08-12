# 05 — Capability provider and consumer

Network plugins benefit from capability contracts because the provider may later move from local memory to Redis, a database, or another implementation without changing consumers.

This example uses `NetworkPlayerApi` as the contract. Feature definitions express provider/consumer roles; consumers use `requireCapability` for required contracts and `findCapability` for optional ones.

Keep a shared capability interface platform-neutral when possible. A Paper-side and Velocity-side application can then depend on the same domain contract even though the providers are different.
