# coding=utf-8
# Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from hashlib import sha1
import os

from pants.base.exceptions import TaskError
from pants.base.fingerprint_strategy import TaskIdentityFingerprintStrategy
from pants.task.task import Task


class NameTagsAndDepFingerprintStrategy(TaskIdentityFingerprintStrategy):
  def compute_fingerprint(self, target):
    hasher = sha1()
    hasher.update(target.address.spec)
    for tag in sorted(target.tags):
      hasher.update(tag)
    return hasher.hexdigest()

  def __hash__(self):
    return 1

  def __eq__(self, other):
    return type(other) == type(self)

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

  @classmethod
  def product_types(cls):
    return ['tagged_build_graph']

  def execute(self):
    basenames = self.get_options().by_basename
    prefixes = self.get_options().by_prefix
    if prefixes or basenames:
      for target in self.context.targets():
        this_basename = os.path.basename(target.address.spec_path)
        target._tags |= set(basenames.get(this_basename, []))
        for prefix in prefixes:
          if target.address.spec.startswith(prefix):
            target._tags |= set(prefixes[prefix])


class BuildGraphRuleViolation(object):
  def __init__(self, target, dep, tag):
    self.target = target
    self.dep = dep
    self.tag = tag
    self.direct = dep in target.dependencies

class TagValidationError(TaskError): pass

class BannedTag(BuildGraphRuleViolation):
  def msg(self):
    return '%s bans dependency on %s (via tag: %s)' % \
      (self.target.address.spec, self.dep.address.spec, self.tag)


class MissingTag(BuildGraphRuleViolation):
  def msg(self):
    return '%s requires dependencies to have tag %s and thus cannot depend on %s' \
        % (self.target.address.spec, self.tag, self.dep.address.spec)


class PrivacyViolation(BuildGraphRuleViolation):
  def msg(self):
    return '%s cannot depend on %s without having tag %s' \
        % (self.target.address.spec, self.dep.address.spec, self.tag)


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
                          fingerprint_strategy=NameTagsAndDepFingerprintStrategy(self),
                          topological_order=True) as invalidation_check:
      for vts in invalidation_check.invalid_vts:
        invalid_targets = vts.targets
        for target in invalid_targets:
          if 'exempt' not in target.tags:
            violations.extend(self.dependee_violations(target))
            violations.extend(self.banned_tag_violations(target))
            violations.extend(self.required_tag_violations(target))

      direct_violations = [v for v in violations if v.direct]

      if direct_violations:
        violations = direct_violations

      for v in violations:
        self.context.log.error(v.msg())

      if violations:
        raise TagValidationError('The graph validation failed, please check the failures above.')

  def extract_matching_tags(self, prefix, target):
    return set([tag.split(':', 1)[1] for tag in target.tags if tag.startswith(prefix)])

  def nonexempt_deps(self, address):
    if address not in self._transitive_closure_cache:
      computed_closure = self.context.build_graph.transitive_subgraph_of_addresses([address])
      self._transitive_closure_cache[address] = [
        dep for dep in computed_closure
        if dep.address != address and
          dep.address not in self.context.build_graph.synthetic_addresses and
          'exempt' not in dep.tags
      ]
    return self._transitive_closure_cache[address]

  def dependee_violations(self, target, include_transitive=True):
    for dep in self.nonexempt_deps(target.address):
      for must_have in self.extract_matching_tags('dependees_must_have:', dep):
        if must_have not in target.tags:
          v = PrivacyViolation(target, dep, must_have)
          if include_transitive or v.direct:
            yield v

  def banned_tag_violations(self, target):
    banned_tags = self.extract_matching_tags('dependencies_cannot_have:', target)
    if banned_tags:
      for dep in self.nonexempt_deps(target.address):
        for banned in banned_tags:
          if banned in dep.tags:
            yield BannedTag(target, dep, banned)

  def required_tag_violations(self, target):
    required_tags = self.extract_matching_tags('dependencies_must_have:', target)
    if required_tags:
      for dep in self.nonexempt_deps(target.address):
        for required in required_tags:
          if required not in dep.tags:
            yield MissingTag(target, dep, required)
