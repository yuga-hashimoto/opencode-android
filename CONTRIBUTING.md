# Contributing to OpenCode Android

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Make your changes
4. Run checks: `./gradlew testDebugUnitTest lintDebug assembleDebug`
5. Commit with a clear message following [Conventional Commits](https://www.conventionalcommits.org/)
6. Push and open a Pull Request against `main`

## Development Setup

- **JDK 17** (Temurin recommended)
- **Android SDK** (API 34)
- **Python 3** (for runtime asset generation)
- Network access on first build (downloads Termux packages)

## Project Structure

```
app/src/main/java/com/opencode/android/
├── data/          # API clients, repositories, models
├── runtime/       # On-device PRoot runtime management
├── ui/            # Jetpack Compose screens and components
└── di/            # Dependency injection
```

## Code Style

- Kotlin with Jetpack Compose
- Follow existing patterns in the codebase
- No comments unless explaining non-obvious logic
- Prefer immutable data classes and sealed interfaces

## Pull Request Guidelines

- Keep PRs focused on a single change
- Include tests for new functionality
- Ensure CI passes (tests, lint, R8 build)
- Update documentation if behavior changes
- Screenshots for UI changes are appreciated

## Reporting Issues

- Use the GitHub issue tracker
- Include device model, Android version, and app version
- For connection issues, note whether you're using local runtime or remote

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
