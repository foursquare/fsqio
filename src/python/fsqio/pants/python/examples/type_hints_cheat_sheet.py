# coding=utf-8
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

"""
To run: `./pants mypy src/python/path/to/target`

See https://docs.python.org/3/library/typing.html and http://mypy.readthedocs.io/en/latest/cheat_sheet.html
for more extensive documentation.
"""

from __future__ import absolute_import, division, print_function

from typing import Any, Callable, Dict, FrozenSet, List, Optional, Set, Text, Tuple, Union


i = 1                       # type: int
f = 1.0                     # type: float
b = True                    # type: bool

# ------------------------------------------------
# Function annotations
# ------------------------------------------------

# NOTE(earellano): there are 3 styles of function annotations: 1) new line, 2) same line, 3) multi-line.


def add_new_line(x, y):
  # type: (int, int) -> int
  return x + y


def add_same_line(x, y):  # type: (int, int) -> int
  return x + y


def add_multi_line(
  x,  # type: int
  y   # type: int
):
  # type: (...) -> int
  return x + y


def void():
  # type: () -> None
  print('side effects!')


# -----------------------------------------------------------
# Text
# -----------------------------------------------------------

"""
NOTE(earellano): Python 2 and Python 3 handle `str` differently. In Python3, `str` means the same thing as Python 2's
`unicode`, whereas in Python 2 `str` means a byte string.

In general, we want to use unicode whenever possible, because it's far saner and is what Python 3 defaults to. So,
always prefer `Text`, a way to consistently refer to unicode across Python 2 and Python 3
(https://docs.python.org/3.7/library/typing.html#typing.Text).

However, it's likely when you first add hints to the code it will be using byte strings. For example, if you don't have
`from __future__ import string_literals`, then "hello" will be type `str`. Until you add unicode semantics to the file,
it is okay to use `str`. Refer to http://wiki.prod.foursquare.com/python/python3-porting-guide.
"""

uni = u'🌎'                 # type: Text  # always prefer this. Requires either u'' or `__future__ unicode_literals`
s = "Hello World!"          # type: str  # only use this if you haven't yet added unicode semantics to the file.
byt = 'Hi'.encode('utf-8')  # type: bytes  # use this when it should actually be bytes


# ------------------------------------------------
# Optional
# ------------------------------------------------

n = None                      # type: None
opt = None if True else "hi"  # type: Optional[str]


# ------------------------------------------------
# Unknown / multiple types
# ------------------------------------------------

# reveal_type(s)      MyPy will print the type it infers when linted (ignore the IntelliJ error). Don't keep in prod.
a = [1, 1.0]          # type: List[Any]
u = [1, 1.0]          # type: List[Union[int, float]]
ignore = ''           # type: ignore

# ------------------------------------------------
# Collections
# ------------------------------------------------

xs = [1, 2, 3]            # type: List[int]
tup = (1, "hi", True)     # type: Tuple[int, str, bool]
dict_ = {'hi': 1}         # type: Dict[str, int]
set_ = {1, 2}             # type: Set[int]
frozen = frozenset({1})   # type: FrozenSet[int]


# ------------------------------------------------
# Standard "duck types"
# ------------------------------------------------

# Use these for duck types, e.g. something that is "list-like" or "dict-like"

# Mapping[K, V]           dict-like values without mutation
# MutableMapping[K, V]    dict-like with mutation
# Iterable[T]             can be used in "for"
# Sequence[T]             supports "len" and "__getitem__"

# ------------------------------------------------
# Higher order functions
# ------------------------------------------------

hof = add_new_line  # type: Callable[[int, int], int]
hof_void = void     # type: Callable[[], None]


# ------------------------------------------------
# Classes
# ------------------------------------------------

# Ignore giving hint to `self` and `cls`.
#
# See https://docs.python.org/3/library/typing.html#classes-functions-and-decorators for more complex usages, e.g.
# inheritance relationships.

class Example:

  # put type hint in init's function parameters, instead of the body
  def __init__(self, x):
    # type: (int) -> None
    self.x = x

  @classmethod
  def classy(cls):
    # type: () -> str
    return 'class method'


example = Example(1)    # type: Example


# ------------------------------------------------
# Type aliases
# ------------------------------------------------

Foo = List[Tuple[int, bool, str]]

foo = [(1, True, "hello")]  # type: Foo

# ------------------------------------------------
# Generics
# ------------------------------------------------

# See https://docs.python.org/3/library/typing.html#generics.
