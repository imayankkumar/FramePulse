# Security policy

## Supported versions

Security fixes go into the latest release and the `main` branch.

## Reporting a vulnerability

Please do not report security problems in a public issue, pull request or discussion.

Report them privately on GitHub instead:

1. Open the [Security tab](https://github.com/imayankkumar/FramePulse/security) of this repository.
2. Choose **Report a vulnerability**.
3. Describe the problem, the affected version, and the steps to reproduce it.

The report is visible only to you and the maintainer. The fix and the advisory are published together once a fixed version is out.

## Scope

FramePulse reads frame timing from the app's own windows and keeps the results in memory. It makes no network requests, writes no files and needs no permissions. Problems in the code that calls FramePulse, or in what an app does with its results, are outside this policy.
