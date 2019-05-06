# Splitter Utils
Fsq.io is a strict subset of Foursquare's production monorepo. We use [Sapling](https://github.com/jsirois/sapling) to build Fsq.io, leveraging `git rev-parse` to isolate just Fsq.io changes into a git branch.

**NOTE:** Both of the wrappers below are optional, Foursquare wrote them to split information in git hooks without relying on Python. The libraries described here are biased to the Fsq.io workflow - you may get more milage from the upstream Sapling by itself unless you have very similar needs.

## Features
Splitter audits every commit and:
1. Splits out any Fsq.io code
1. Audits/Redacts the commit message.
1. Injects any file moves.

## Usage:
**NOTE**: Config examples from Fsq.io can been seen farther down.

Best set in CI (the example assumes use of sapling_util.py's single `opensource` split):

      # Split out the new git branch with Sapling
      pip install sapling
      python2.7 src/python/fsqio/splitter/sapling/sapling_util.py \
          --paths-file=fsqio/files/opensource_files.txt
      sapling.py --split <split_name>

      # Move files and redact commit messages.

      python27 src/python/fsqio/splitter/codebase/history_rewriter.py \
          --commit-range="_sapling_split_<split_name>_" \
          --contributor-file=fsqio/contributors/opted_in_employees.txt \
          --line-redactions-file=fsqio/commit_messages/commit_message_line_removals.txt \
          --match-redactions-file=fsqio/commit_messages/commit_message_match_removals.txt \
          --file-moves-json-file=fsqio/files/file_moves.json

We then cherry-pick the new commits onto the fsqio/master branch as a pure fast-forward to avoid munging history with a force-push.

## Config
### Splitting commits
This is straightforward - define the files + directories that you want split into a branch.

config example (`fsqio/files/opensource_files.txt`):

        # This is the list of files that are split out to opensource.
        # If you are adding a whole directory, it MUST end with a '/'!

        3rdparty/BUILD.opensource
        3rdparty/python/BUILD.opensource
        BUILD.opensource
        build-support/fsqio/
        fsqio/files/deployed_files/
        pants.ini
        scripts/fsqio/
        src/docs/fsqio/
        src/jvm/io/fsq/
        src/python/fsqio/
        src/resources/io/fsq/
        src/thrift/io/fsq/
        test/python/fsqio_test/
        test/jvm/fsqio.tests.policy
        test/jvm/io/fsq/
        test/thrift/io/fsq/
        upkeep

As you can see, we have isolated under the `fsqio/io.fsq/opensource` namespaces for the most part, which is nice UX but not a technical requirement.

### Moving Files
Sometimes you want to deploy a file that doesn't make sense for your source repo. An example from Fsq.io is the CONTRIBUTING.md file, which is useful for an open source project but not internally.

The history rewriter provides an API for this by leveraging  `git rev-parse`. Every commit that is split by Sapling will move the file from `source` -> `dst`.

The config is json to avoid managing key-values data structure in bash.
config example (`fsqio/files/file_moves.json`):

        {
          "fsqio/files/deployed_files/opensource.CLA.md": "CLA.md",
          "fsqio/files/deployed_files/opensource.CONTRIBUTING.md": "CONTRIBUTING.md",
          "fsqio/files/deployed_files/opensource.gitignore": ".gitignore",
          "fsqio/files/deployed_files/opensource.LICENSE.md": "LICENSE.md",
          "fsqio/files/deployed_files/opensource.pants-travis-ci.ini": "pants-travis-ci.ini",
        }

This can overwrite existing files or add new locations, and is safe to run over old commits that predate either/both of those paths.

### Commit redacting

This is somewhat specialized to Fsq.io's needs, since we are programmatically splitting OSS from our internal IP. We may details that only make sense or are appropriate to reference internally - this are some rough tools to try and redact that type of data from commit messages.

#### Commit author anonymity
Since we push our split repo to open source, contributors must opt-in  before we push commits credited to their name/email.

The default author is `"Opensource <opensource@foursquare.com>"` but can be re-configured to your best fit by passing the proper options to `history_rewriter.py`:

        --default-author-name="Foo" --default-email="bar@you.you"

#### Commit message lines/match redaction
This accepts patterns to remove declared patterns or alternatively, any line containing a match. These are separate options for legacy reasons.
config example(`fsqio/commit/messages/commit_message_line_removals.txt`):

          Auditors:
          Reviewers:
          Revision:
          Subscribers:
          Test Plan:

config example(`fsqio/commit/messages/commit_message_match_removals.txt`):

          Opensource:[[:space:]]*
          DEV.*[0-9:-]:
          WEB.*[0-9:-]:
          <etc>

## Development process
I typically iterate against a local split made with the sapling_util, but you could use pure sapling. In our case, the sapling-created branch called `_sapling_split_opensource_`.

Then I can iterate on the history rewriter using just a subset of that branch, and reset it to the original every loop.

          $  orig_sha=$(git log _sapling_split_opensource_)
          $  commit_range="_sapling_split_opensource_~10..._sapling_split_opensource_"
          $  python src/python/fsqio/splitter/codebase/history_rewriter.py --commit-range=${commit_range} --match-redactions-file=fsqio/commit_messages/commit_message_match_removals.txt --line-redactions-file=fsqio/commit_messages/commit_message_line_removals.txt


Inspect the commits of `_sapling_split_opensource_` to audit your redactions and then reset its state to iterate.

          $  git update-ref refs/heads/_sapling_split_opensource_ ${orig_sha}


## Limitations
1. Ideally we would package this as a library, but the Sapling entry point is non-conducive to that workflow.
1. No incremental updates. Our Fsq.io build happens once per-day, commits are cherry-picked as a group directly onto Fsq.io master.
1. Sapling branch names are not flexible - they will always be `_sapling_split_<split_name>_`. This resulted in some non-intuitive defaults.
1. Haphazard API - this was grown organically around the strict Fsq.io requirements - other use cases will likely require inline changes.
1. Need tests...
