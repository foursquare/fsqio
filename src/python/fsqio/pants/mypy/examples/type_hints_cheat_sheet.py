# coding=utf-8
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

"""
To run: `./fs mypy src/python/path/to/target`

See https://docs.python.org/3/library/typing.html and http://mypy.readthedocs.io/en/latest/cheat_sheet.html
for more extensive documentation.
"""

from __future__ import absolute_import, print_function

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

# NOTE(earellano): Python2 and Python3 handle `str` differently. In Python3, `str` means the same thing as Python2's
# `unicode`, which is a representation of Unicode char-points but not an actual bytes object that can be used by IO.
# In Python2, meanwhile, `str` is stored as a byte string.
#
# In general, represent string literals with `str`. Use `Text` if you want unicode support.
#
# See https://timothybramlett.com/Strings_Bytes_and_Unicode_in_Python_2_and_3.html for more info.

s = "Hello World!"          # type: str
byt = 'Hi'.encode('utf-8')  # type: bytes
uni = u'🌎'                 # type: Text


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
