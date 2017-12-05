#!/bin/bash
set -e

echo "Nuking .pyc files in a background process..."
# This task is run as a forked subshell, since we have seen this task take 10-20 mins for some web and android devs.
(find "${BUILD_ROOT}" -name '*.pyc' -delete) &
