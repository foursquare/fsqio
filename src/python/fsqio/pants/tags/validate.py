# coding=utf-8
# Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from hashlib import sha1
import os
import textwrap

from pants.base.exceptions import TaskError
from pants.base.fingerprint_strategy import FingerprintStrategy
from pants.task.task import Task


class NameTagsAndDepFingerprintStrategy(FingerprintStrategy):
  def compute_fingerprint(self, target):
    hasher = sha1()
    hasher.update(target.address.spec)
    for tag in sorted(target.tags):
      hasher.update(tag)
    return hasher.hexdigest()

  def __hash__(self):
    return 1

  def __eq__(self, other):
    return isinstance(other, type(self))


class Tagger(Task):
  @classmethod
  def register_options(cls, register):
    super(Tagger, cls).register_options(register)
    register(
      '--by-basename',
      type=dict,
      fingerprint=True,
      default={},
      advanced=True,
    )
    register(
      '--by-prefix',
      type=dict,
      fingerprint=True,
      default={},
      advanced=True,
    )
    register(
      '--by-tag',
      type=dict,
      fingerprint=True,
      default={},
      advanced=True,
    )

  @classmethod
  def product_types(cls):
    return ['tagged_build_graph']

  def execute(self):
    basenames = self.get_options().by_basename
    prefixes = self.get_options().by_prefix
    tags = self.get_options().by_tag

    if prefixes or basenames or tags:
      for target in self.context.targets():
        this_basename = os.path.basename(target.address.spec_path)
        target._tags |= set(basenames.get(this_basename, []))
        for prefix, p_tags in prefixes.items():
          if target.address.spec.startswith(prefix):
            target._tags |= set(p_tags)
        for tag, t_tags in tags.items():
          if tag in target._tags:
            target._tags |= set(t_tags)


class BuildGraphRuleViolation(object):
  def __init__(self, target, dep, tag):
    self.target = target
    self.dep = dep
    self.tag = tag
    self.direct = dep in target.dependencies


class TagValidationError(TaskError):
  pass


class BannedTag(BuildGraphRuleViolation):
  def msg(self):
    return '{} bans dependency on {} (via tag: {})'.format(
      self.target.address.spec, self.dep.address.spec, self.tag)


class MissingTag(BuildGraphRuleViolation):
  def msg(self):
    return '{} requires dependencies to have tag {} and thus cannot depend on {}'.format(
      self.target.address.spec, self.tag, self.dep.address.spec)


class MissingOneOfTag(BuildGraphRuleViolation):
  def msg(self):
    return '{} requires dependencies to have at least one tag from {} and thus cannot depend on {}'.format(
      self.target.address.spec, self.tag, self.dep.address.spec)


class MustHaveViolation(BuildGraphRuleViolation):
  def msg(self):
    return '{} cannot depend on {} without having tag {}'.format(
      self.target.address.spec, self.dep.address.spec, self.tag)


class MustHaveOneOfViolation(BuildGraphRuleViolation):
  def msg(self):
    return '{} cannot depend on {} without having one of the following tags {}'.format(
      self.target.address.spec, self.dep.address.spec, self.tag)


class FSCommonViolation(BuildGraphRuleViolation):
  def msg(self):
    return textwrap.dedent("""{} cannot have '{}' tag because it is not in 'src/jvm/io/fsq/'
      or 'src/jvm/com/foursquare/common'""".format(
        self.target, self.tag))


class Validate(Task):
  def __init__(self, *args, **kwargs):
    super(Validate, self).__init__(*args, **kwargs)
    self._transitive_closure_cache = {}

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require_data('tagged_build_graph')

  @classmethod
  def product_types(cls):
    return ['validated_build_graph']

  def execute(self):
    if 'buildgen' in self.context.requested_goals:
      return
    violations = []

    with self.invalidated(self.context.targets(),
                          invalidate_dependents=True,
                          fingerprint_strategy=NameTagsAndDepFingerprintStrategy(),
                          topological_order=True) as invalidation_check:
      for vts in invalidation_check.invalid_vts:
        invalid_targets = vts.targets
        for target in invalid_targets:
          if 'exempt' not in target.tags:
            violations.extend(self.dependee_violations(target))
            violations.extend(self.banned_tag_violations(target))
            violations.extend(self.required_tag_violations(target))
            violations.extend(self.fscommon_violations(target))

      direct_violations = [v for v in violations if v.direct]

      if direct_violations:
        violations = direct_violations

      for v in violations:
        self.context.log.error(v.msg())

      if violations:
        raise TagValidationError('The graph validation failed, please check the failures above.')

  def extract_matching_tags(self, prefix, target):
    return {tag.split(':', 1)[1] for tag in target.tags if tag.startswith(prefix)}

  def nonexempt_deps(self, address):
    if address not in self._transitive_closure_cache:
      computed_closure = self.context.build_graph.transitive_subgraph_of_addresses([address])
      self._transitive_closure_cache[address] = [
        dep for dep in computed_closure
        if (
          dep.address != address and
          dep.address not in self.context.build_graph.synthetic_addresses and
          'exempt' not in dep.tags
        )
      ]
    return self._transitive_closure_cache[address]

  def dependee_violations(self, target, include_transitive=True):
    for dep in self.nonexempt_deps(target.address):
      for must_have in self.extract_matching_tags('dependees_must_have:', dep):
        if must_have not in target.tags:
          violation = MustHaveViolation(target, dep, must_have)
          if include_transitive or violation.direct:
            yield violation

      for must_have_one_of in self.extract_matching_tags('dependees_must_have_one_of:', dep):
        has_one = False
        for one_of in must_have_one_of.split(','):
          if one_of.strip() in target.tags:
            has_one = True
            break
        if not has_one:
          violation = MustHaveOneOfViolation(target, dep, must_have_one_of)
          if include_transitive or violation.direct:
            yield violation

  def fscommon_violations(self, target):
    if('fscommon' in target.tags and 'should_remove_fscommon_tag' not in target.tags):
      path = target.address.spec_path
      # TODO:Jamie untangle resources, but ignore for now
      allowed = [
        'com/foursquare/common',
        'com/twitter/finagle',
        'io/fsq',
        'src/resources',
        'src/webapp',
        'test/resources',
      ]
      if not any(sub in target.address.spec_path for sub in allowed):
          yield FSCommonViolation(target, dep=None, tag='fscommon')

  def required_tag_violations(self, target):
    required_tags = self.extract_matching_tags('dependencies_must_have:', target)
    if required_tags:
      for dep in self.nonexempt_deps(target.address):
        for required in required_tags:
          if required not in dep.tags:
            yield MissingTag(target, dep, required)

    required_tags = self.extract_matching_tags('dependencies_must_have_one_of:', target)
    if required_tags:
      required_tags = map(lambda x: x.split(','), required_tags)
      for dep in self.nonexempt_deps(target.address):
        has_one = False
        for tag_group in required_tags:
          for tag in tag_group:
            if tag.strip() in dep.tags:
              has_one = True
              break
        if not has_one:
          yield MissingOneOfTag(target, dep, required_tags)

  def banned_tag_violations(self, target):
    banned_tags = self.extract_matching_tags('dependencies_cannot_have:', target)
    if banned_tags:
      for dep in self.nonexempt_deps(target.address):
        for banned in banned_tags:
          if banned in dep.tags:
            yield BannedTag(target, dep, banned)
