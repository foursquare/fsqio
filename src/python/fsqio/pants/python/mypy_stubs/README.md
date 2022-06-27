# MYPY Stubs

This directory is configured as the default `MYPYPATH` for pants. You can write `.pyi` interface stubs and place them in this directory, and `pants` will use them when running `mypy`. This is useful for adding types to 3rdparty libraries that do not have them.

See https://mypy.readthedocs.io/en/stable/stubs.html for more information on mypy stub files