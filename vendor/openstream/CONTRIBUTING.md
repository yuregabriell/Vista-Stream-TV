# Contributing to OpenStream

Thank you for considering contributing to OpenStream. This document outlines the process and guidelines for contributing to the project.

## Code of Conduct

Be respectful and professional in all interactions. We aim to maintain a welcoming environment for all contributors.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a new branch for your feature or bugfix
4. Make your changes
5. Test thoroughly
6. Submit a pull request

## Development Setup

### Prerequisites
- JDK 17
- Android Studio (latest stable version)
- TMDB API key (add to `local.properties`)

### Building the Project
```bash
./gradlew assembleDebug
```

### Running Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Development Guidelines

### Architecture
- Follow Clean Architecture principles
- Maintain clear separation between domain, data, and presentation layers
- Use dependency injection via Hilt
- Keep ViewModels focused on state management

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Write self-documenting code with minimal comments
- Keep functions small and focused on a single responsibility

### UI Development
- Use Material Design 3 Expressive components
- Check for Expressive versions before using standard M3 components
- Never create custom components that duplicate M3 functionality
- Follow the design rules in `CLAUDE.md`
- Maintain consistent spacing and layout patterns

### Commit Messages
Use clear, descriptive commit messages written for whoever reads `git log` in a
month: imperative, 72 characters or fewer, naming the user-visible change
rather than the files touched. Add a body when the reason is not obvious.
```
Add search history persistence

- Implement SharedPreferences storage
- Add JSON serialization for search queries
- Update SearchViewModel to load/save history
```

If someone using the app would notice your change, end the commit message with
a `Changelog:` section: one `- ` bullet per change, each on a single line, in
plain words for someone using the app (no internals, class or file names, or
filler). Those bullets are sent to beta testers with the build. Only git
trailers (`Token: value` lines) may follow the section. Omit it for
docs/CI/refactors with no user-visible effect.

```
Fix the queue jumping back to the top after a reorder

Changelog:
- Fixed the queue scrolling back to the top after you move a song.
```

Write each bullet so it stands alone: several commits' bullets arrive in one
message with no commit boundaries between them. Open with a clear verb (Added,
Fixed, Improved), describe a fix as the bug looked to the user, and read the
list back as a user before committing.

### Pull Request Process

1. **Branch from `beta` and open the pull request against `beta`.** `beta` is
   where new work lands and what beta testers get builds from; `main` only
   moves when a tested `beta` is released. A pull request opened against
   `main` will be retargeted before it is reviewed. Open it with
   `gh pr create --base beta` and merge into `beta` with a merge commit,
   never a squash (a squash folds several `Changelog:` sections into one and
   the testers' post comes out mangled).
2. **For anything bigger than a small fix, open an issue first** so the
   approach can be agreed before you spend time on it.
   [docs/ROADMAP.md](docs/ROADMAP.md) lists what is planned.

3. **Read [CLAUDE.md](CLAUDE.md) before you write code.** It is short, and it
   holds the rules that are bugs to break even when the build passes (no
   hardcoded colors, how sources are resolved, how Room migrations are
   written).
4. **Keep a pull request to one topic** and fill in the template, including
   screenshots or a recording for anything visible.

A pull request from a fork does not get a CI build on its own when it comes
from an outside contributor; that is deliberate, because building it means
running the fork's Gradle build with the project's runners. A maintainer
builds it when reviewing. Once it is merged into `beta`, a signed build goes
to the beta testers.

5. **Update Documentation**
   - Update `CLAUDE.md` if you change architecture or add major features
   - Update README.md if you add user-facing features
   - Add inline code documentation for complex logic

6. **Test Your Changes**
   - Ensure the app builds without errors
   - Test on multiple screen sizes if UI changes
   - Verify no regressions in existing functionality

7. **Keep PRs Focused**
   - One feature or bugfix per PR
   - Avoid mixing refactoring with feature additions
   - Keep changes as small as reasonably possible

8. **PR Description**
   - Clearly describe what the PR does
   - Reference any related issues
   - Include screenshots for UI changes
   - List any breaking changes

### Code Review

All submissions require review. We use GitHub pull requests for this purpose. Reviewers will check for:
- Code quality and adherence to guidelines
- Architecture consistency
- Test coverage
- Documentation updates
- Performance implications

## What to Contribute

### Good First Issues
- UI polish and refinements
- Error message improvements
- Documentation updates
- Test coverage additions

### Feature Requests
Before implementing a new feature:
1. Open an issue to discuss the feature
2. Wait for maintainer feedback
3. Ensure it aligns with project goals
4. Follow the agreed-upon approach

### Bug Reports
When reporting bugs, include:
- Android version and device model
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs or screenshots
- TMDB API response if network-related

### Areas for Contribution

**Sources (no Kotlin needed):**
- Add, fix or retire streaming sources in the extension catalog. See
  [extensions/README.md](extensions/README.md).

**App:**
- Reliable dubs: dub routes return links locked to the provider's server, so playback on devices
  is unreliable
- Paging for long search results and catalogs
- Accessibility audits (TalkBack, font scaling, contrast)
- Tablet and foldable layouts
- Localization

## Dependency Management

- All versions are managed in `libs.versions.toml`
- Research dependencies before adding new ones
- Verify artifact IDs and versions
- Prefer stable releases over alpha/beta when possible
- Document why a dependency is needed

## Testing Guidelines

### Unit Tests
- Test ViewModels and business logic
- Mock dependencies using appropriate frameworks
- Aim for meaningful test coverage, not just high percentages

### UI Tests
- Test critical user flows
- Use Compose testing APIs
- Keep tests maintainable and readable

## Documentation

### Code Documentation
- Document complex algorithms or business logic
- Explain "why" not "what" in comments
- Keep documentation up-to-date with code changes

### Architecture Documentation
- Update `CLAUDE.md` for architectural changes
- Document design decisions in commit messages
- Add diagrams for complex flows if helpful

## Questions?

If you have questions about contributing:
- Check existing issues and discussions
- Review `CLAUDE.md` for codebase context
- Open a discussion for general questions
- Open an issue for specific problems

## Recognition

Contributors will be recognized in release notes and the project README. Significant contributions may result in collaborator status.

---

Thank you for contributing to OpenStream!
