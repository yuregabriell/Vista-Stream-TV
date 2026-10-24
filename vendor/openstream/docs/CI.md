# CI and release announcements (`.github/workflows/`)

Three workflows, and **every one hangs off an `authorize` job that runs first**: `build.yml`
(debug and release APKs, uploaded as artifacts, plus the beta post), `codeql.yml` (`actions` and
`java-kotlin`, manual build mode running `assembleDebug` because autobuild guesses wrong on
Android projects), and `telegram-notify.yml` (star milestones and release announcements).
`build.yml` and `codeql.yml` run for pushes to, and pull requests into, both `main` and `beta`.
Workflow-level `permissions: {}` is the default in all three; each job re-adds only what it needs.

**Judge APK size from the minified release, never the debug APK.** The debug APK carries full
Compose tooling while the release is minified with `isShrinkResources`. `androidResources.localeFilters`
must stay identical to `res/xml/locales_config.xml`: without it, transitive libraries advertise
language configurations the app does not support and inflate `resources.arsc`.

**The authorize gate is not a formality and its denials are not bugs to fix.** A run proceeds only
when the actor has write access: a same-repo PR is authorized by construction, pushes and
`workflow_dispatch` need the actor to be the owner or in `TRUSTED_ACTORS`, and a fork PR needs
`author_association` of `OWNER`, `MEMBER` or `COLLABORATOR`. A fork PR from an outside contributor
builds nothing, deliberately, because analysing it means running that fork's Gradle build on a
runner. Those runs appear as `action_required` and even once approved the gate skips every build
job and writes a "Build skipped" summary. Widening the lists is the owner's call per contributor,
not a cleanup.

**A red run with no logs is GitHub, not the code.** `The job was not acquired by Runner of type
hosted even after multiple attempts`, with an empty `runner_name` and nothing from
`gh run view --log-failed`, means no runner ever picked it up. Re-run rather than debug.

**Action versions are the Node 24 majors** (`checkout@v7`, `setup-java@v6`, `upload-artifact@v7`).
The v4/v5 line targeted Node 20 and ran only because the runner forced it onto Node 24, with a
deprecation warning on every job.

**`telegram-notify.yml` does not trigger on push.** It fires on `watch` (stars) and `release:
published` only, so a change cannot be verified by pushing and the first live run of any edit is a
real release. Test it by extracting the `run:` block and executing it with `curl` and `jq` stubbed.

**Those changelog bullets are read by the beta testers, on a phone, as their brief.** That is what
the accumulated `Changelog:` sections become when the APK is delivered - not release-note filler
but the list of things somebody is about to go and check. Write each bullet so it names what
visibly changed and where, in the words of someone using the app rather than someone who read the
diff, and keep it standing alone: several commits' bullets arrive in one message with no commit
boundaries between them.

**The APK post is a separate, authorized path inside `build.yml`, and it runs only on a push to
`beta`.** A pull request builds its APKs as artifacts and posts nothing. Posting per pull request
worked while one PR was open at a time and falls apart with two: each posts a "Latest beta"
lacking the other's changes, the pin follows whichever pushed last, and each changelog is measured
from that PR's previous build rather than from the APK a tester had installed. The release job
still cannot run until `authorize` allows it; merging a PR into `beta` is a push by whoever merged
it, so the owner merging a contributor's PR is what delivers it, and a fork PR on its own never
reaches the signing or bot secrets.

After the signed universal APK reaches the beta chat (or its over-limit artifact link does), the
same step sends the accumulated `Changelog:` sections from every commit after the head of the last
successful push run on `beta`. The APK message is labelled **Latest beta**, pinned with
`disable_notification=true`, then the formerly most-recent pin is removed only after the new pin
succeeds; pin permissions are best-effort and must never turn a successful APK delivery into a
failed build. It reads the changelog checkpoint from Actions history with `actions: read`;
canceled and failed runs therefore do not advance it, so their entries roll into the next delivered
APK without a writable state file. On the first delivery, an unavailable history response, or a
rewritten `beta`, it falls back to everything `beta` has that `main` does not - a possible repeat
is safer than a silent omission. The caption carries the version and the commit and no PR number or
author: a push to `beta` can be a merge of anybody's PR or a fix pushed straight to it. Changelogs
are plain text, chronological, and split below Telegram's UTF-16 message ceiling.

**Two kinds of push to `beta` post nothing, and the run stays green.** No commits since the
checkpoint (the branch was created or fast-forwarded onto a commit already delivered), and a range
with no changelog entry whose changed paths are all docs, workflows or screenshots. Both write a
"Beta post skipped" line to the run summary, which is where to look when an expected APK did not
arrive. The path test is an exclusion list on purpose: a path it does not name counts as an app
change, so a gap in the list posts an APK nobody needed rather than withholding one somebody did. A
range that carries a changelog entry is always delivered whatever it touched.

**Merges into `beta` are merge commits.** The changelog is read per commit, so a squash - one
message holding every commit's subject and `Changelog:` section - publishes the subjects between
the sections as bullets.

**Telegram's HTML mode is a small, strict parser that rejects the whole message on one malformed
tag.** It understands `b/i/u/s/a/code/pre/blockquote/tg-spoiler` and nothing else, capped at 4096
UTF-16 units. Release notes are free-text Markdown, so `notes_to_html` escapes `&`, `<` and `>`
first and only then adds tags we control, and **every conversion rule is line-local on purpose** -
that is what makes trimming whole lines to fit the budget tag-balanced. The notes budget is
whatever the header, asset list and footer leave over, not a constant. If Telegram rejects the
markup anyway the message is resent as plain text, because a release with no announcement is worse
than one announced unformatted.

**Free text reaches the script as data, never through `${{ }}` interpolation into the shell.**
Release bodies and names arrive as environment variables, while APK changelog subjects and bullets
are read from Git after checkout. `github.event.release.assets` arrives as `toJSON(...)` and can
legitimately be empty, since a release is often published before its APKs finish uploading.

**`OWNER_URL` is the author-link convention, and it lives only in `telegram-notify.yml`.**
`author_html()` renders the repository owner's login as a link to `https://ivorisnoob.lol` and
anyone else as plain text, matched case-insensitively against `github.repository_owner`.

**Git trailers after the changelog are skipped.** The extractor publishes every line after
`Changelog:`, and a `Co-Authored-By:` or `Signed-off-by:` trailer has to be the last paragraph of
a message to be a trailer at all, so it would land in the testers changelog as a bullet. Trailers
are dropped by shape: any line reading `Token: value`, where the token is a word or hyphenated
words. Changelog lines are `- ` bullets and cannot match; a continuation line that happens to
start with `Word: ` would be dropped too, so keep each bullet on one line.
