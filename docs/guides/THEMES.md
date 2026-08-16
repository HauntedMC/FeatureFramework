# Programmatic Message Themes

Themes let a separately versioned library own a named colour palette while application messages refer to stable semantic names. A host can register multiple themes; theme and item identifiers are matched case-insensitively.

## Define a theme

Theme libraries only need `featureframework-theme-api` and Adventure API:

```java
public final class ExampleTheme {
    public static final Theme THEME = Theme.builder("Example")
            .solid("Brand", "#A855F7")
            .solid("Text", "#E2E8F0")
            .gradient("Heading", List.of(
                    TextColor.color(0xA855F7),
                    TextColor.color(0x38BDF8)))
            .build();

    private ExampleTheme() {
    }
}
```

Themes and their item collections are immutable. Blank or malformed identifiers, empty themes, invalid effect arguments, and case-insensitive duplicates fail immediately while the host is being composed.

## Register themes

Register themes before building a Paper or Velocity host:

```java
featureHost = PaperFeatureHost
        .builder(this, MyPlugin.class, BuiltInFeatures.collection())
        .theme(ExampleTheme.THEME)
        .theme(PartnerTheme.THEME)
        .build();
```

The equivalent Velocity builder has the same `theme(...)` and `themes(...)` methods. `host.themes()` exposes the immutable registry for inspection. Every feature localization object opened by that host uses the same registry.

## Use theme tags

Messages can use a theme item anywhere a normal MiniMessage colour tag is accepted:

```yaml
friends.none: '<Example:Brand>◆ Friends  <Example:Text>No friends are online'
```

The syntax is `<theme-id:item-id>`. Theme identifiers are the identifiers registered on the host and may not conflict with standard MiniMessage tag names. `</theme-id>` closes a scoped solid, gradient, or rainbow item; a later colour tag can also replace a persistent colour. Transition items follow MiniMessage transition semantics and represent a calculated persistent colour.

Before MiniMessage parsing, FeatureFramework resolves references whose theme identifier is registered on the host to standard MiniMessage tags. An invalid item of a registered theme is removed while its visible text remains, and each distinct problem is logged once. Escaped references such as `\<Example:Brand>` remain literal text.

Theme expansion occurs after platform and named placeholder expansion. Values inserted as strings are therefore trusted MiniMessage, matching the existing localization placeholder contract; escape or use component placeholders for untrusted player input.

Keep palette libraries small and platform-neutral. Applications that only need direct `TextColor` mappings should depend on a palette artifact that does not require FeatureFramework; a separate adapter artifact can construct the framework `Theme`.
