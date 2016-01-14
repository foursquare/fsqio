#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

export PANTS_CONFIG_OVERRIDE="['pants.ini']"

# The script below runs the pants bootstrap task and exports PANTSBINARY. Basically a noop if the pants_version
# hasn't changed. This could be hooked more properly into upkeep but we are waiting on the need to arise.
source scripts/upkeep/opensource-tasks/opensource-pants-env.sh
