<!--
Pull requests go to the `beta` branch, not `main`. Check the "base" dropdown
at the top of this page says `beta` before you open the pull request. See
CONTRIBUTING.md.
-->

## What this changes

<!-- A short description of the change and the problem it solves. Link the issue if there is one. -->

Fixes #

## Why

<!-- What was wrong, or what the feature is for. If the real problem turned out to be a layer below the reported symptom, say so here. -->

## States handled

<!--
For anything user-facing, which of these were considered and what happens in each.
Delete the ones that genuinely do not apply rather than leaving them blank.
-->

- Loading:
- Empty:
- Error / offline:
- Long titles, missing artwork:
- Landscape / tablet:

## Deliberately not done

<!-- Anything left out on purpose, and why. This is useful information, not an admission. -->

## Screenshots or recording

<!-- Required for any UI change. -->

## Checklist

- [ ] Builds with `./gradlew :app:compileDebugKotlin`
- [ ] Run on a device, not just compiled (maintainer tests if you cannot)
- [ ] No hardcoded colors; everything resolves through `MaterialTheme.colorScheme`
- [ ] Material 3 Expressive components used before standard M3 where one exists
- [ ] Edge-to-edge holds, 48dp touch targets, headings marked for screen readers

### If this touches one of these areas

- [ ] **Playback or source change**: named the undocumented third-party behavior it relies on in the PR description
- [ ] **Room schema change**: a real `Migration` in `di/DatabaseModule.kt`, not just a version bump
- [ ] **Extension catalog change**: `app/src/main/assets/extensions/official-repo.json` is a byte-identical copy of `extensions/index.json` (`OfficialCatalogTest` checks)
- [ ] **User-visible change**: commit messages end with a `Changelog:` section (see CONTRIBUTING.md)
- [ ] No TMDB API keys, keystore material, or user data committed
