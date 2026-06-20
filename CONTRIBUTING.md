# Contributing to MagicBox

MagicBox is the Android frontend for the MagicNet Magisk module backend.

## Scope

Keep backend execution out of the APK. Features should be modeled as frontend
flows that call, display, or prepare data for MagicNet CLI/MCP contracts.

## Build

```sh
./gradlew :app:assembleDebug
```

## License

By contributing, you agree that your contribution is distributed under the
project license: GNU Affero General Public License v3.0 or later.
