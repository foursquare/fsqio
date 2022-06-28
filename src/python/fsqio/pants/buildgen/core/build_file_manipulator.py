# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import ast
from difflib import unified_diff
import logging
import os
import re
import sys

import colors
from pants.build_graph.address import Address
from typing import List


logger = logging.getLogger(__name__)


class BuildTargetParseError(Exception):
  pass


class DependencySpec(object):
  """A representation of a single dependency spec, including comments around it.

  This is a helper class to aid in deduplicating, sorting, forcing, and formatting
  dependency specs in a BUILD target's dependencies section.
  """

  def __init__(self, spec, comments_above=None, side_comment=None, indent=4):
    self.spec = spec
    self.comments_above = comments_above or []
    self.side_comment = side_comment
    self.indent = indent

  def comments_above_lines(self):
    for line in self.comments_above:
      line = line.strip()
      if line:
        yield '# {line}'.format(line=line)
      else:
        yield ''

  def indented_lines(self, lines):
    indent_spaces = ' ' * self.indent

    for line in lines:
      line = line.strip()

      if not line:
        yield ''
      else:
        yield '{indent_spaces}{line}'.format(indent_spaces=indent_spaces, line=line)

  def lines(self):
    spec_line = "'{0}',".format(self.spec)
    if self.side_comment is not None:
      spec_line = '{spec_line}  # {comment}'.format(spec_line=spec_line,
                                                    comment=self.side_comment)
    comments_above = list(self.comments_above_lines())
    lines = comments_above + [spec_line]
    return list(self.indented_lines(lines))

  def has_comment(self):
    # If all of the comments above are whitespace, don't consider this forced,
    # but keep the whitespace.
    return bool(any(self.comments_above_lines()) or self.side_comment)

  def __repr__(self):
    return '\n'.join(self.lines())


class BuildFileManipulator(object):
  """A class to load, represent, and change the dependencies of a given target.

  Use BuildFileManipulator.load(...) for construction, rather than constructing it directly.
  """

  @classmethod
  def detect_indentation(cls, lines):
    # type: (List[str]) -> int
    DEFAULT_INDENT = 2
    rg = re.compile(r"(^[ \t]{2,})")

    guess = None
    for l in lines:
      if not l.startswith(' '):
        continue

      m = rg.match(l)
      if not m:
        continue

      indent = len(m.group())
      if guess is None or indent < guess:
        guess = indent
      elif indent % guess != 0:
        return DEFAULT_INDENT

    return guess or DEFAULT_INDENT

  @classmethod
  def load(cls, address, target_aliases, artifact_type=None, provides=None):
    """A BuildFileManipulator factory class method.

    Note that BuildFileManipulator requires a very strict formatting of target declaration.
    In particular, it wants to see a newline after `target_type(`, `dependencies = [`, and
    the last param to the target constructor before the trailing `)`.  There are further
    restrictions as well--see the comments below or check out the example targets in
    the tests for this class.

    :param address: of type BuildFileAddress
    :param name: The name of the target (without the spec path or colon) to operate on.
    :target aliases: The callables injected into the build file context that we should treat
      as target declarations.
    :provides list[string]: A list of provides lines provided by the buildgen task.
      Will be codegen into the target unless an existing provides is preceded by a comment string.
      Additional opt-outs by target type or path are offered by the task options.
    """
    name = address.target_name
    with open(address.rel_path, 'r') as f:
      source = f.read()
    source_lines = source.split('\n')
    indent = cls.detect_indentation(source_lines)
    tree = ast.parse(source)

    # Since we're not told what the last line of an expression is, we have
    # to figure it out based on the start of the expression after it.
    # The interval that we consider occupied by a given expression is
    # [expr.lineno, next_expr.lineno).  For the last expression in the
    # file, its end is the number of lines in the file.
    # Also note that lineno is 1-indexed, so we subtract 1 from everything.
    intervals = [t.lineno - 1 for t in tree.body]
    intervals.append(len(source_lines))

    # Candidate target declarations
    top_level_exprs = [t for t in tree.body if isinstance(t, ast.Expr)]
    top_level_calls = [e.value for e in top_level_exprs if isinstance(e.value, ast.Call)]

    # Just in case someone is tricky and assigns the result of a target
    # declaration to a variable, though in general this is not useful
    assigns = [t for t in tree.body if isinstance(t, ast.Assign)]
    assigned_calls = [t.value for t in assigns if isinstance(t.value, ast.Call)]

    # Final candidate declarations
    calls = top_level_calls + assigned_calls

    # Filter out calls that don't have a simple name as the function
    # i.e. keep `foo()` but not `(some complex expr)()`
    calls = [call for call in calls if isinstance(call.func, ast.Name)]

    # Now actually get all of the calls to known aliases for targets
    # TODO(pl): Log these
    target_calls = [call for call in calls if call.func.id in target_aliases]

    # We now have enough information to instantiate a BuildFileTarget for
    # any one of these, but we're only interested in the one with name `name`
    def name_from_call(call):
      for keyword in call.keywords:
        if keyword.arg == 'name':
          if isinstance(keyword.value, ast.Str):
            return keyword.value.s
          else:
            logger.warning('Saw a non-string-literal name argument to a target while '
                           'looking through {address}.  Target type was {target_type}.'
                           'name value was {name_value}'
                           .format(address=address,
                                   target_type=call.func.id,
                                   name_value=keyword.value))
      return os.path.basename(address.spec_path)

    calls_by_name = dict((name_from_call(call), call) for call in target_calls)
    if name not in calls_by_name:
      raise BuildTargetParseError('Could not find target named {name} in {address}'
                                  .format(name=name, address=address))

    target_call = calls_by_name[name]

    # lineno is 1-indexed
    target_interval_index = intervals.index(target_call.lineno - 1)
    target_start = intervals[target_interval_index]
    target_end = intervals[target_interval_index + 1]

    def is_whitespace(line):
      return line.strip() == ''

    def is_comment(line):
      return line.strip().startswith('#')

    def is_ignored_line(line):
      return is_whitespace(line) or is_comment(line)

    # Walk the end back so we don't have any trailing whitespace
    while is_ignored_line(source_lines[target_end - 1]):
      target_end -= 1

    target_source_lines = source_lines[target_start:target_end]

    # TODO(pl): This would be good logging
    # print(ast.dump(target_call))
    # print("Target source lines")
    # for line in target_source_lines:
    #   print(line)

    if target_call.args:
      raise BuildTargetParseError('Targets cannot be called with non-keyword args.  Target was '
                                  '{name} in {address}'
                                  .format(name=name, address=address))

    # TODO(pl): This should probably be an assertion.  In order for us to have extracted
    # this target_call by name, it must have had at least one kwarg (name)
    if not target_call.keywords:
      raise BuildTargetParseError('Targets cannot have no kwargs.  Target type was '
                                  '{target_type} in {address}'
                                  .format(target_type=target_call.func.id, address=address))

    if target_call.lineno == target_call.keywords[0].value.lineno:
      raise BuildTargetParseError('Arguments to a target cannot be on the same line as the '
                                  'target type.   Target type was {target_type} in {address} '
                                  'on line number {lineno}.'
                                  .format(target_type=target_call.func.id,
                                          address=address,
                                          lineno=target_call.lineno))

    for keyword in target_call.keywords:
      kw_str = keyword.arg
      kw_start_line = keyword.value.lineno
      source_line = source_lines[kw_start_line - 1]
      kwarg_line_re = re.compile(r'\s*?{kw_str}\s*?=\s*?\S'.format(kw_str=kw_str))

      if not kwarg_line_re.match(source_line):
        raise BuildTargetParseError('kwarg line is malformed.  The value of a kwarg to a target '
                                    'must start after the equals sign of the line with the key.'
                                    'Build file was: {address}.  Line number was: {lineno}'
                                    .format(address=address, lineno=keyword.value.lineno))

    # Same setup as for getting the target's interval
    target_call_intervals = [t.value.lineno - target_call.lineno for t in target_call.keywords]
    target_call_intervals.append(len(target_source_lines))

    last_kwarg = target_call.keywords[-1]
    last_interval_index = target_call_intervals.index(last_kwarg.value.lineno - target_call.lineno)
    last_kwarg_start = target_call_intervals[last_interval_index]
    last_kwarg_end = target_call_intervals[last_interval_index + 1]
    last_kwarg_lines = target_source_lines[last_kwarg_start:last_kwarg_end]
    if last_kwarg_lines[-1].strip() != ')':
      raise BuildTargetParseError('All targets must end with a trailing ) on its own line.  It '
                                  "cannot go at the end of the last argument's line.  Build file "
                                  'was {address}.  Target name was {target_name}.  Line number '
                                  'was {lineno}'
                                  .format(address=address,
                                          target_name=name,
                                          lineno=last_kwarg_end + target_call.lineno))

    # Now that we've double checked that we have the ) in the proper place,
    # remove that line from the lines owned by the last kwarg
    target_call_intervals[-1] -= 1

    # TODO(pl): Also good logging
    # for t in target_call.keywords:
    #   interval_index = target_call_intervals.index(t.value.lineno - target_call.lineno)
    #   print("interval_index:", interval_index)
    #   start = target_call_intervals[interval_index]
    #   end = target_call_intervals[interval_index + 1]
    #   print("interval: {}, {}".format(start, end))
    #   print("lines:")
    #   print('\n'.join(target_source_lines[start:end]))
    #   print('\n\n')
    # print(target_call_intervals)

    def get_dependencies_node(target_call):
      for keyword in target_call.keywords:
        if keyword.arg == 'dependencies':
          return keyword.value
      return None

    dependencies_node = get_dependencies_node(target_call)
    dependencies = []
    if dependencies_node:
      if not isinstance(dependencies_node, ast.List):
        raise BuildTargetParseError('Found non-list dependencies argument on target {name} '
                                    'in build file {address}.  Argument had invalid type '
                                    '{node_type}'
                                    .format(name=name,
                                            address=address,
                                            node_type=type(dependencies_node)))
      last_lineno = dependencies_node.lineno
      for dep_node in dependencies_node.elts:
        if not dep_node.lineno > last_lineno:
          raise BuildTargetParseError('On line number {lineno} of build file {address}, found '
                                      'dependencies declaration where the dependencies argument '
                                      'and dependencies themselves were not all on separate lines.'
                                      .format(lineno=dep_node.lineno, address=address))

        # First, we peek up and grab any whitespace/comments above us
        peek_lineno = dep_node.lineno - 1
        comments_above = []
        while peek_lineno > last_lineno:
          peek_str = source_lines[peek_lineno - 1].strip()
          if peek_str == '' or peek_str.startswith('#'):
            comments_above.insert(0, peek_str.lstrip(' #'))
          else:
            spec = dependencies[-1].spec if dependencies else None
            raise BuildTargetParseError('While parsing the dependencies of {target_name}, '
                                        'encountered an unusual line while trying to extract '
                                        'comments.  This probably means that a dependency at '
                                        'line {lineno} in {address} is missing a trailing '
                                        'comma.  The string in question was {spec}'
                                        .format(target_name=name,
                                                lineno=peek_lineno,
                                                address=address,
                                                spec=spec))
          peek_lineno -= 1

        # Done peeking for comments above us, now capture a possible inline side-comment
        dep_str = source_lines[dep_node.lineno - 1]
        dep_with_comments = dep_str.split('#', 1)
        side_comment = None
        if len(dep_with_comments) == 2:
          side_comment = dep_with_comments[1].strip()
        dep = DependencySpec(
          dep_node.s,
          comments_above=comments_above,
          side_comment=side_comment,
          indent=indent * 2,
        )
        # TODO(pl): Logging here
        dependencies.append(dep)
        last_lineno = dep_node.lineno

      deps_interval_index = target_call_intervals.index(dependencies_node.lineno -
                                                        target_call.lineno)
      deps_start = target_call_intervals[deps_interval_index]
      deps_end = target_call_intervals[deps_interval_index + 1]

      # Finally, like we did for the target intervals above, we're going to roll back
      # the end of the deps interval so we don't stomp on any comments after it.
      while is_ignored_line(target_source_lines[deps_end - 1]):
        deps_end -= 1

    else:
      # If there isn't already a place defined for dependencies, we use
      # the line interval just before the trailing ) that ends the target
      deps_start = -1
      deps_end = -1

    ######
    # Provides support injected here.
    ######

    # Finding a comment above an existing provides toggles this to True - buildgen will
    # overwrite the provides when forced.
    provides_forced = False

    # If the task provides a list of provides lines (inferred from target address),
    # look for existing provides lines in case there already are provides forced with a comment.
    if provides:
      def get_provides_node(target_call):
        for keyword in target_call.keywords:
          if keyword.arg == 'provides':
            return keyword.value
        return None

      provides_node = get_provides_node(target_call)
      if provides_node:
        # NOTE(mateo): Only support buildgen opt-out comments directly before an existing provides.
        # Opting out entirely requires adding the target address to the blocklist in the options.
        last_provides_lineno = provides_node.lineno

        # Peek up and grab any whitespace/comments above us
        provides_peek_lineno = provides_node.lineno - 1
        provides_comment = []
        provides_peek_str = source_lines[provides_peek_lineno - 1].strip()
        if provides_peek_str.startswith('#'):
          provides_comment.insert(0, provides_peek_str.lstrip(' #'))
          provides_forced = True

        provides_interval_index = target_call_intervals.index(
          provides_node.lineno - target_call.lineno
        )
        provides_start = target_call_intervals[provides_interval_index]
        provides_end = target_call_intervals[provides_interval_index + 1]

        while is_ignored_line(target_source_lines[provides_end - 1]):
          provides_end -= 1
      else:
        provides_start = -1
        provides_end = -1
    else:
      provides_start = -1
      provides_end = -1

    return cls(name=name,
               address=address,
               build_file_source_lines=source_lines,
               target_source_lines=target_source_lines,
               target_interval=(target_start, target_end),
               dependencies=dependencies,
               dependencies_interval=(deps_start, deps_end),
               provides=provides,
               provides_interval=(provides_start, provides_end),
               provides_forced=provides_forced,
               indent=indent)

  def __init__(self,
               name,
               address,
               build_file_source_lines,
               target_source_lines,
               target_interval,
               dependencies,
               dependencies_interval,
               provides,
               provides_interval=None,
               provides_forced=None,
               indent=2):
    """See BuildFileManipulator.load() for how to construct one as a user."""
    self.name = name
    self.target_address = address
    self.provides_forced = provides_forced

    self._build_file_source_lines = build_file_source_lines
    self._target_source_lines = target_source_lines
    self._target_interval = target_interval
    self._dependencies_interval = dependencies_interval
    self._dependencies_by_address = {}
    self._provides = provides
    self._provides_interval = provides_interval
    self._indent = indent

    for dep in dependencies:
      dep_address = Address.parse(dep.spec, relative_to=address.spec_path)
      if dep_address in self._dependencies_by_address:
        raise BuildTargetParseError('The address {dep_address} occurred multiple times in the '
                                    'dependency specs for target {name} in {address}. '
                                    .format(dep_address=dep_address.spec,
                                            name=name,
                                            address=address))
      self._dependencies_by_address[dep_address] = dep

  def get_dependency_addresses(self):
    return self._dependencies_by_address.keys()

  def add_dependency(self, address):
    """Add a dependency to this target.  This will deduplicate existing dependencies."""
    if address in self._dependencies_by_address:
      if self._dependencies_by_address[address].has_comment():
        logger.warn('BuildFileManipulator would have added {address} as a dependency of '
                    '{target_address}, but that dependency was already forced with a comment.'
                    .format(address=address.spec, target_address=self.target_address.spec))
        return
    spec = address.reference(referencing_path=self.target_address.spec_path)
    self._dependencies_by_address[address] = DependencySpec(spec, indent=self._indent * 2)

  def clear_unforced_dependencies(self):
    """Remove all dependencies not forced by a comment.

    This is useful when existing analysis can infer exactly what the correct dependencies should
    be.  Typical use is to call `clear_unforced_dependencies`, then call `add_dependency` for each
    dependency inferred from analysis.  The resulting dependency set should be the pruned set
    of all dependencies, plus dependencies hand forced by a user comment.
    """
    self._dependencies_by_address = dict(
      (address, dep) for address, dep in self._dependencies_by_address.items()
      if dep.has_comment()
    )

  def dependency_lines(self):
    """The formatted dependencies=[...] lines for this target.

    If there are no dependencies, this returns an empty list.
    """
    deps = sorted(self._dependencies_by_address.values(), key=lambda d: d.spec)

    def dep_lines():
      yield '{}dependencies = ['.format(' ' * self._indent)
      for dep in deps:
        for line in dep.lines():
          yield line
      yield '{}],'.format(' ' * self._indent)
    return list(dep_lines()) if deps else []

  def provides_lines(self):

    provides = self._provides

    # The target's provides text will be constructed in the buildgen_target's class.
    # It can read its own config to identify artifact type, spec_path and so on.
    def formatted_provides():
      yield '{}provides = {}('.format(' ' * self._indent, "scala_artifact")
      for line in provides:
        yield '{}{}'.format(' ' * self._indent * 2, line.strip())
      yield '{}),'.format(' ' * self._indent)
    return list(formatted_provides())

  def get_provides_lines(self):
    # If forced with a comment, use the pre-existing provides call from the BUILD file.
    # Otherwise, use the provides passed by the buildgen task.
    if self.provides_forced:
      provides_begin, provides_end = self._provides_interval
      provides_lines = self._target_source_lines[provides_begin:provides_end]
    else:
      provides_lines = self.provides_lines()
    return provides_lines

  def target_lines(self):
    """The formatted target_type(...) lines for this target.

    This is just a convenience method for extracting and re-injecting the changed
    `dependency_lines` into the target text.
    """
    target_lines = self._target_source_lines[:]
    deps_begin, deps_end = self._dependencies_interval
    target_lines[deps_begin:deps_end] = self.dependency_lines()
    if self._provides:
      provides_begin, provides_end = self._provides_interval
      target_lines[provides_begin:provides_end] = self.get_provides_lines()
    return target_lines

  def build_file_lines(self):
    """Like `target_lines`, the entire BUILD file's lines after dependency manipulation."""
    build_file_lines = self._build_file_source_lines[:]
    target_begin, target_end = self._target_interval
    build_file_lines[target_begin:target_end] = self.target_lines()
    return build_file_lines

  def diff_lines(self):
    """A diff between the original BUILD file and the resulting BUILD file."""
    start_lines = self._build_file_source_lines[:]
    end_lines = self.build_file_lines()
    diff_generator = unified_diff(start_lines,
                                  end_lines,
                                  fromfile=self.target_address.rel_path,
                                  tofile=self.target_address.rel_path,
                                  lineterm='')
    return list(diff_generator)

  def write(self, dry_run=True, use_colors=True, fail_on_diff=False):
    """Write out the changes made to the BUILD file, and print the diff to stderr.

    :param dry_run: Don't actually write out the BUILD file, but do print the diff to stderr.
    :param colors: If False, no colors will be used in the terminal output.
    """

    def terminal_msg(diff_lines):

      def maybe_color(color_fn, msg):
        return color_fn(msg) if use_colors else msg

      msg = ('\n\n')
      if dry_run:
        msg += maybe_color(colors.yellow, 'DRY RUN, would have written this diff:')
      else:
        msg += maybe_color(colors.blue, 'REAL RUN, wrote the following diff:')
      msg += maybe_color(colors.yellow, ('\n' + '*' * 40 + '\n'))
      msg += 'target at: '
      msg += str(self.target_address) + '\n'

      for line in diff_lines:
        color_fn = str
        if line.startswith('+') and not line.startswith('+++'):
          color_fn = colors.green
        elif line.startswith('-') and not line.startswith('---'):
          color_fn = colors.red
        msg += maybe_color(color_fn, (line + '\n'))
      msg += maybe_color(colors.yellow, ('*' * 40 + '\n'))
      return msg

    diff_lines = self.diff_lines()
    if diff_lines:
      sys.stderr.write(terminal_msg(diff_lines))
      # In CI we want the option to fail the build if buildgen writes a diff.
      # Note: This will write out the diff for the first incorrect build file and then exit. There may be other changes
      # which will be picked up if --fail-on-diff is not passed.
      if fail_on_diff:
        print("Buildgen wrote a diff. Run `./pants buildgen` locally and commit the buildfile diff with your changes.")
        sys.exit(1)
      if not dry_run:
        with open(self.target_address.rel_path, 'w') as f:
          f.write('\n'.join(self.build_file_lines()))
