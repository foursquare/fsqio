# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.base.payload_field import JarsField
from pants.util.memo import memoized_property

from fsqio.pants.ivy.target_bag_mixin import TargetBagMixin


class GlobalClasspathTaskMixin(TargetBagMixin):

  SYNTHETIC_TARGET_NAME = 'global_classpath_bag'

  @classmethod
  def injected_target_name(cls):
    return cls.SYNTHETIC_TARGET_NAME

  @classmethod
  def gathered_target_type_aliases(cls):
    return ('jar_library',)

  @memoized_property
  def bag_target_closure(self):
    # This returns a Twitter.common.OrderedSet. Turning into a set for ease, relucant and reckless as it may be.
    return set(self.context.build_graph.get_target(self.get_synthetic_address()).closure())

  @classmethod
  def add_payload_fields(cls, build_graph, addresses, payload):
    # JarLibrary targets have a unique attribute called `managed_dependencies`, which holds a spec of a
    # `managed_jar_dependency` target. That will not be inserted along with the rest of the jar_library's closure
    # since at address_mapping time it is not a dependency. We could take care to track them down and insert them
    # but it looks to me like this handling is already wired into the JarDependency and JarLibrary pipeline. If we
    # end up seeing misses, we can add the logic to insert them as a special case, but for now I hope to hand that
    # special casing off.
    all_jar_deps = JarLibrary.to_jar_dependencies(
      cls.get_synthetic_address(),
      [t.spec for t in addresses],
      build_graph,
    )
    payload.add_fields({
      'jars': JarsField(sorted(all_jar_deps)),
    })
    return payload
