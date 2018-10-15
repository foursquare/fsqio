# ivy

## Reports
This version of the ivy goal produces json reports that will be stored at `resolution_report_outdir` if `disable_reports` is set to False.

## Fail on diff
Additionnaly there is a `fail-on-diff` that will cause pants to abort if the current resolve is different from the report on disk. This can be, for example, usefull for keeping track of dependency changes over time.
