# Contributing to LudoKing Controller

Thanks for helping improve the project.

## Before opening an issue

- Search existing issues first.
- For bugs, include device model, Android version, app version, reproduction steps and a redacted log.
- Never include signing credentials, pairing tokens, private IP addresses or personal data.

## Local development

1. Fork and clone the repository.
2. Open it in Android Studio or use the Gradle wrapper.
3. Copy `signing.properties.example` only if you need local release signing; keep the copy untracked.
4. Run `./gradlew assembleDebug` before opening a pull request.
5. Test both apps on Android API 21+ when changing networking, permissions or overlay behavior.

## Pull requests

- Keep one focused change per pull request.
- Explain the user-visible behavior and testing performed.
- Update the README when commands, permissions, protocol behavior or setup steps change.
- Prefer small, reviewable commits.
- Do not commit generated APKs, keystores, passwords or IDE metadata.

The Android CI workflow must pass before merging. By contributing, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
