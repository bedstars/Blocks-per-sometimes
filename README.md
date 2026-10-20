# Blocks per sometimes (Fabric)

Shows blocks / crops / fish per second, per 10 minutes and per hour at the top-left of the screen.
Open the settings with `/bpsmod`. `/bpsmod reset` clears the stats.

## Get the .jar without installing anything (GitHub builds it for you)

1. Create a free account at github.com and click **New repository** (any name, Public or Private).
2. On the empty repo page click **uploading an existing file** and drag in EVERYTHING from this folder
   (`build.gradle`, `settings.gradle`, `gradle.properties`, the `src` folder, ...). Commit.
3. Hidden folders sometimes don't upload. Check that `.github/workflows/build.yml` exists in the repo.
   If not: **Add file > Create new file**, type `.github/workflows/build.yml` as the name and paste in the
   contents of that file from this zip. Commit.
4. Open the **Actions** tab. A "Build Blocks per sometimes" run starts by itself (or press "Run workflow").
5. When it is green (2-4 minutes), click the run and download the artifacts at the bottom:
   `blocks-per-sometimes-mc26.1.2` and `blocks-per-sometimes-mc26.2` (each is a zip containing the .jar).
6. Put the jar in your `mods` folder together with Fabric API.

If a job is red, open it, copy the error text and send it to me - I'll fix the code.
