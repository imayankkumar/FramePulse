# Contributing to FramePulse

All pull requests are welcome: bug fixes, tests, docs and new features.

## Before you start

- **Bug:** open an issue with the device, the Android version, how you started the session, what you saw and what you expected.
- **New feature or public API change:** open an issue first, so we can agree on the API before you write the code.
- **Security problem:** do not open an issue. See [SECURITY.md](SECURITY.md).

## How to build and test

You need JDK 17–23 (Gradle 8.13 does not run on newer JDKs).

```bash
# Build everything, run the unit tests and check the public API
./gradlew build test

# Run the instrumentation tests on a connected device or emulator (screen on and unlocked)
./gradlew :framepulse:connectedDebugAndroidTest
```

CI runs the same build, and the instrumentation tests on emulators with API 21, 24 and 34. A pull request needs a green CI before it is merged.

## Public API

The public API is recorded in `framepulse/api/framepulse.api`, and the build fails when the compiled library differs from it. If you change the public API on purpose, update the file and commit it with your change:

```bash
# Record the current public API
./gradlew :framepulse:apiDump
```

Adding to the API is fine. Removing or changing something already in the file breaks apps compiled against an earlier version, so discuss it in an issue first.

## Code

- Every public declaration states its visibility (explicit API mode) and has KDoc.
- Android framework code and implementation details go in the `internal` package.
- New behaviour comes with a test: JVM unit tests where possible, instrumentation tests for code that needs a real window or `Choreographer`.
- Only claim in docs what the code does and a test verifies.

## Commit messages

Follow [How to Write a Git Commit Message](https://cbea.ms/git-commit):

- Separate the subject from the body with a blank line.
- Keep the subject to 50 characters, capitalized, in the imperative mood ("Add", not "Added"), with no period at the end.
- Wrap the body at 72 characters, and use it to explain what changed and why.

## Pull requests

- One change per pull request.
- The title is a short imperative sentence, like a commit subject.
- The description is a few bullets starting with `* New:`, `* Update:`, `* Fix:` or `* Remove:`.
- Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for any change users will notice.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
