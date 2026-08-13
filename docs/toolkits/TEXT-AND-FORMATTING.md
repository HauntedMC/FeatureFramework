# Text, Formatting, and Safe Player Input

`nl.hauntedmc.featureframework.toolkit.text` is a platform-neutral text toolkit built on Adventure. It gives a large plugin one consistent answer to a deceptively difficult question: how should legacy formatting, MiniMessage, user input, URLs, placeholders, and serialized components move through the application?

Use it in normal feature classes, services, commands, web integrations, and shared API modules. It does not require a Paper or Velocity runtime.

## Choose a trust boundary first

Do not use the same parser policy for every string. The useful split is:

| Input | Recommended policy |
|---|---|
| Administrator-authored config and localization | Allow the MiniMessage features the template needs; templates are trusted. |
| Player chat, nicknames, mail, database text, or external webhooks | Explicitly allow only the features the product promises, and retain tag sanitization. |
| Legacy plugin/config migration | Normalize mixed legacy and MiniMessage input once, then store or render the canonical result. |
| Audit, search, Discord, logs, and plain APIs | Convert to plain text deliberately instead of leaking formatting tokens. |

This distinction matters on a network: accepting `<click:run_command:...>`, selectors, NBT, or hover payloads in a player-controlled field is a product decision, not a convenience default.

## Parse mixed input safely

`ComponentFormatter` normalizes legacy ampersand/section codes, legacy hex variants, MiniMessage, and plain text before parsing an Adventure `Component`. It can then sanitize tags that are not in an explicit allowlist.

```java
import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import nl.hauntedmc.featureframework.toolkit.text.format.TextFormatter;

Component publicAnnouncement = ComponentFormatter.deserialize(rawPlayerText)
        .expect(TextFormatter.InputFormat.MIXED_INPUT)
        .features(
                ComponentFormatter.Feature.COLORS,
                ComponentFormatter.Feature.DECORATIONS,
                ComponentFormatter.Feature.CLICK
        )
        .autoLinkUrls()
        // true is the default: unsupported tags are removed while their text remains.
        .sanitizeUnknownTags(true)
        .toComponent();
```

The example permits colors, decorations, and automatically linked URLs. It does **not** grant player input arbitrary hover, selector, NBT, or custom-tag capabilities. `Feature.CLICK` controls whether player-authored click tags survive sanitization; `autoLinkUrls()` deliberately creates trusted click events after parsing. Do not call `autoLinkUrls()` when links should not be interactive.

For a trusted localization template, allow the standard resolver set explicitly:

```java
Component message = ComponentFormatter.deserialize("<gradient:aqua:blue>Network ready</gradient>")
        .expect(TextFormatter.InputFormat.MINIMESSAGE)
        .features(ComponentFormatter.ALL_DEFAULTS())
        .sanitizeUnknownTags(false)
        .toComponent();
```

The formatter instances are stateless. The fluent converter returned by `deserialize(...)` is single-use; create a new one for each conversion.

## Normalize, serialize, and strip strings

`TextFormatter` is the string-to-string companion. It is useful at an integration boundary where parsing a component is unnecessary.

```java
import nl.hauntedmc.featureframework.toolkit.text.format.TextFormatter;

String canonicalMiniMessage = TextFormatter.convert("&aOnline: &#35D07F42")
        .expect(TextFormatter.InputFormat.MIXED_INPUT)
        .toMiniMessage();

String legacyForAnOlderIntegration = TextFormatter.convert(canonicalMiniMessage)
        .expect(TextFormatter.InputFormat.MINIMESSAGE)
        .options(options -> options.xRepeatedHex(true))
        .toLegacy('&');

String searchableAuditText = TextFormatter.toPlain(canonicalMiniMessage);
String safelyEmbeddedName = TextFormatter.escapeForMiniMessage(playerSuppliedName);
```

Supported inputs include `&a`, `§a`, `&#RRGGBB`, Bungee-style `&x&F&F...`, MiniMessage hex tags, and the common accidental `<##RRGGBB>` form. `InputFormat` is both documentation of the boundary and a way to avoid transforms you do not need. Use `MIXED_INPUT` for migration-facing input and the narrower values for known formats.

When serializing to legacy clients or APIs, the options can preserve hex, downsample it to named legacy colors, choose `&` versus `§`, and choose the Bungee repeated-hex shape.

## Inspect before applying a policy

`FormatInspector` detects formatting in raw strings or existing `Component`s. This is helpful for moderation rules, migration diagnostics, or a config validation command.

```java
import nl.hauntedmc.featureframework.toolkit.text.format.inspect.FormatInspector;

if (FormatInspector.containsAnyFormatting(rawNickname)) {
    // Apply the network nickname policy, or reject this field.
}

if (FormatInspector.hasAnyFormatting(componentFromAnIntegration)) {
    // The value is not suitable for a plain-text audit field without conversion.
}
```

`TextPatterns` exposes the immutable, compiled patterns behind those utilities when a feature needs a precise validation rule: Minecraft names, URLs, formatting codes, tags, version strings, and date-name extraction. Prefer the higher-level formatter or inspector for conversion; use the patterns for focused validation.

## Typed placeholders outside localization

For a message assembled outside the localization API, `MessagePlaceholders` avoids repeated ad-hoc replacement code and supports strings, numbers, and Adventure components.

```java
import nl.hauntedmc.featureframework.toolkit.text.placeholder.MessagePlaceholders;

MessagePlaceholders values = MessagePlaceholders.builder()
        .addString("server", serverName)
        .addNumber("online", onlinePlayers)
        .build();

String rendered = MessagePlaceholders.applyPlaceholders(
        "<green>{server}</green>: {online} players online", values);
```

Placeholder keys are applied longest-first, so `{player_name}` cannot be accidentally changed by a `{player}` replacement. Placeholder values are not escaped automatically: escape a value with `TextFormatter.escapeForMiniMessage(...)` when it is untrusted and will be inserted into a MiniMessage template.

## A durable network convention

For large applications, establish this convention once and use it everywhere:

```text
untrusted text -> ComponentFormatter with a small allowlist -> Component
trusted template -> localization/ComponentFormatter with intended tags -> Component
Component -> TextFormatter/ComponentFormatter serialization -> external system
any formatted input -> TextFormatter.toPlain() -> audit/search/index
```

That makes presentation behavior reviewable, keeps legacy compatibility out of domain code, and prevents every feature from inventing its own parsing and sanitization rules.
