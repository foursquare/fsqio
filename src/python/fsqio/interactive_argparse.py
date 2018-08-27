# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import sys

import argparse
from builtins import input
import colors


class _InteractiveModeAction(argparse.Action):
  def __init__(self, option_strings, dest, default=False, required=False, help=None):
    super(_InteractiveModeAction, self).__init__(
      option_strings=option_strings,
      dest=dest,
      nargs=0,
      default=default,
      required=required,
      help=help
    )

  def _convert_to_boolean(self, value):
    value_lower = value.lower()
    if value_lower in ('y', 'yes'):
      return True
    elif value_lower in ('n', 'no'):
      return False
    else:
      return None  # Didn't parse

  def __call__(self, parser, namespace, values, option_string):
    # I know this import looks out of place but importing readline has side effects (namely,
    # enabling readline support for all input), so I import it here so at least the side effects
    # are restricted to this method call and not importing this module.
    import readline
    for arg in parser.interactive_args:
      required = arg.get('was_required', False)
      is_boolean = arg.get('action') in ('store_true', 'store_false')

      maybe_default = '(default={})'.format(arg['default']) if 'default' in arg else None
      maybe_optional = '[optional]' if (not required and not is_boolean) else None
      maybe_boolean = '[Y/n]' if is_boolean else None

      modifiers = filter(lambda x: x, [maybe_default, maybe_optional, maybe_boolean])
      prompt = colors.bold('{}{}{}? '.format(
        arg['help'],
        (' ' if len(modifiers) > 0 else ''),  # Space if modifiers nonempty
        ' '.join(modifiers)
      ))
      value = input(prompt)
      # Repeat while input is invalid.
      while (
        (value.strip() == '' and required) or
        (is_boolean and self._convert_to_boolean(value) is None)
      ):
        value = input(prompt)
      if is_boolean:
        value = self._convert_to_boolean(value)
      elif not required:
        value = value or None  # Convert the empty string to None.
      python_name = InteractiveArgumentParser._convert_to_python_name(arg['name'])
      setattr(namespace, python_name, value)


class InteractiveArgumentParser(argparse.ArgumentParser):
  """
  Special hack on top of ArgumentParser that lets you pass an "-i" or "--interactive" argument
  to enter an interactive mode where you're prompted for all the argument values.

  When using this with subparsers, just pass it as the parser_class to add_subparsers.

  Caveats:
   - Only supports named (not positional) arguments.
   - Only supports the following actions: store, store_true, store_false.
  """

  @staticmethod
  def _convert_to_python_name(flag_name):
    """Converts --foo-bar into foo_bar"""
    return flag_name.lstrip('-').replace('-', '_')

  def __init__(self, *args, **kwargs):
    self.initialized = False
    super(InteractiveArgumentParser, self).__init__(*args, **kwargs)
    self.interactive_args = []
    self.add_argument(
      '-i',
      '--interactive',
      action=_InteractiveModeAction,
      help='provide arguments interactively (pass this as the only param)'
    )
    self.initialized = True

  def add_argument(self, *args, **kwargs):
    if self.initialized:
      # Picks the name with the most "-" characters in front of it.
      longest_name = max(args, key=lambda x: x.count('-', 0, 2))
      kwargs_and_extra = {'name': longest_name, 'was_required': kwargs.get('required', False)}
      kwargs_and_extra.update(kwargs)
      kwargs['required'] = False  # So that in interactive mode it won't fail.
      self.interactive_args.append(kwargs_and_extra)
    super(InteractiveArgumentParser, self).add_argument(*args, **kwargs)

  def parse_known_args(self, args=None, namespace=None):
    if len(args or []) == 0:
      self.print_help()
      sys.exit(2)
    namespace, args = super(InteractiveArgumentParser, self).parse_known_args(args, namespace)
    # Have to do our own checking of required args (note parse_args calls parse_known_args)
    for arg in self.interactive_args:
      python_name = InteractiveArgumentParser._convert_to_python_name(arg['name'])
      if arg['was_required'] and getattr(namespace, python_name, None) is None:
        raise Exception('argument {} is required'.format(arg.get('metavar') or arg['name']))
    return (namespace, args)
