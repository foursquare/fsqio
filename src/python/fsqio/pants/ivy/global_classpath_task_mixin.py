# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.base.build_environment import get_buildroot
from pants.base.payload import Payload
from pants.base.payload_field import JarsField
from pants.base.specs import DescendantAddresses, SiblingAddresses
from pants.build_graph.address import Address
from pants.build_graph.target import Target


class GlobalClasspathTaskMixin(object):
  """Task Mixin that will scan configured paths and inject discovered targets into the build_graph."""

  # Override these at Task level as needed- these defaults are pretty bad.
  SYNTHETIC_TARGET_NAME = 'global_classpath_bag'
  SYNTHETIC_TARGET_PATH = get_buildroot()

  # Implementing classes that need overrides are offered a few basic levers:
  #  - inject_synthetic_target - allows configuring the synthetic target bag.
  #  - maybe_add_dependency_edges - can add further dependency edges as needed.
  #  - new_target_roots - Can choose to expand the original target_roots or redefine altogether.

  @classmethod
  def prepare(cls, options, round_manager):
    super(GlobalClasspathTaskMixin, cls).prepare(options, round_manager)

  # I am not including these options in any fingerprint bc all that matters is the relationship between targets.
  @classmethod
  def register_options(cls, register):
    super(GlobalClasspathTaskMixin, cls).register_options(register)
    register(
      '--jar-paths',
      type=list,
      default=[],
      advanced=True,
      help='Include JarLibrary targets that are found when non-recursively scanning these file paths.'
    ),
    register(
      '--jar-roots',
      type=list,
      advanced=True,
      default=['3rdparty'],
      help='Include JarLibrary targets that are found by recursively scanning these directories.'
    ),
    # I could not find a sensible way to include the buildroot in the other options, so fell back to a yes/no.
    register(
      '--include-buildroot',
      type=bool,
      advanced=True,
      default=False,
      help='Include any JarLibrary targets defined in the buildroot.'
    ),

  @classmethod
  def get_synthetic_address(cls):
    # TODO(mateo): Add an error catch in case the address is already in the graph (meaning two implementing tasks),
    # which means one or both must also override the constants used for the addressing.
    return Address(spec_path='', target_name=cls.SYNTHETIC_TARGET_NAME)

  @classmethod
  def inject_synthetic_target(cls, build_graph, synthetic_address, *args, **kwargs):
    """Create a synthetic target that depends on the set of jar_library_targets.

    The created target is injected into the build graph as an unconnected target with a payload of
    a JarsField populated by the JarDependencies implied by the jar_library_targets.
    The synthetic target's address can be passed as an argument or set as an override with cls.get_synthetic_address.

    :param `pants.build_graph.BuildGraph` build_graph: Populated build_graph instance
    :param `pants.build_graph.Address` synthetic_address: Address to be used by the injected target.
    :param *args: Any args required by the synthetic target type.
    :param **kwargs: Keywords required by the synthetic target type.
    :returns target:
    :rtype subclass of `pants.target.Target`:
    """
    build_graph.inject_synthetic_target(synthetic_address, Target, *args, **kwargs)
    return build_graph.get_target(synthetic_address)

  @classmethod
  def maybe_add_dependency_edges(cls, build_graph, original_targets, synthetic_target):
    """Opportunity to connect the synthetic_target to any target that was originally in the build graph.

    This passes the targets found in the build_graph before any synthetic targets were created, to try and
    limit the opportunities for accidental build graph cycles.

    :param `pants.build_graph.BuildGraph` build_graph: Populated build_graph instance.
    :param collection[`pants.target.Target`]: Set of targets originally found in build_graph.
    :param subclass of `pants.target.Target` target: The synthetic target created by the task.

    :param **kwargs: Keywords required by the synthetic target type.
    :returns target:
    :rtype subclass of `pants.target.Target`:
    """
    # Default is to opt-out and keep the target as an unconnected component, which is neither helpful nor disruptive.
    pass

  @classmethod
  def new_target_roots(cls, build_graph, synthetic_target):
    """Return a list of targets that will become the new target_roots for the entire Pants run.

    :param `pants.build_graph.BuildGraph` build_graph: Populated build_graph instance.
    :param subclass of `pants.target.Target` target: The synthetic target created by the task.
    :returns List of targets that will replace the original target_roots or None to leave them unchanged.
    :rtype list or None:
    """
    # Default of None means the mixin does not redefine the target_roots, but just inject new targets into the graph.
    # The options is surfaced in case it is needed but AFAICT the best solution is to just pull the target bag into
    # context by injecting it as a dependency through the cls.maybe_add_dependency_edges.
    #
    # One other thing to be aware of is that today(Pants 1.4.0 and earlier) any given invocation of Pants is allowed to
    # propose a single alternate_target_root. There are some upstream tasks that do so (in `changed` goal, primarily)
    # so returning anything more than None here will make the implementing class incompatible with those and any other
    # tasks that propose new target_roots. FYI.
    return None

  @classmethod
  def create_synthetic_target(cls, options, address_mapper, build_graph, discovered_targets):
    """Create a synthetic target that depends on the set of jar_library_targets.

    The created target is injected into the build graph as an unconnected target with a payload of
    a JarsField populated by the JarDependencies implied by the jar_library_targets.

    :param `pants.option.options.Option` options: The Task's scoped options.
    :param `pants.build_graph.AddressMapper` address_mapper: Populated build_graph instance.
    :param `pants.build_graph.BuildGraph` build_graph: Populated build_graph instance
    :param collection[`pants.target.Target`] discovered_targets: Targets newly injected into build graph but possibly
      not in the context of any target_root.
    :returns synthetic target:
    :rtype subclass of `pants.target.Target`:
    """
    synthetic_address = cls.get_synthetic_address()

    # JarLibrary targets have a unique attribute called `managed_dependencies`, which holds a spec of a
    # `managed_jar_dependency` target. That will not be inserted along with the rest of the jar_library's closure
    # since at address_mapping time it is not a dependency. We could take care to track them down and insert them
    # but it looks to me like this handling is already wired into the JarDependency and JarLibrary pipeline. If we
    # end up seeing misses, we can add the logic to insert them as a special case, but for now I hope to hand that
    # special casing off.
    jar_library_targets = [t for t in discovered_targets if isinstance(t, JarLibrary)]
    all_jar_deps = JarLibrary.to_jar_dependencies(
      synthetic_address,
      [t.address.spec for t in jar_library_targets],
      build_graph,
    )
    payload = Payload()
    payload.add_fields({
      'jars': JarsField(sorted(all_jar_deps)),
    })
    synthetic_target = cls.inject_synthetic_target(
      build_graph,
      synthetic_address,
      payload=payload,
      dependencies=[j.address for j in jar_library_targets],
    )
    return synthetic_target

  @classmethod
  def alternate_target_roots(cls, options, address_mapper, build_graph):
    discovered_targets = set()
    original_targets = build_graph.targets()

    # The address macros return OrderedSets, which is not considered here. I believe that to be fine but FYI.
    buildroot = set([SiblingAddresses('')]) if options.get('include_buildroot') else set()
    paths_checked = set(SiblingAddresses(t) for t in options.get('jar_paths'))
    dirs_checked = set(DescendantAddresses(t) for t in options.get('jar_roots'))
    spec_sets = buildroot | paths_checked | dirs_checked

    gathered_addresses = address_mapper.scan_specs(spec_sets)
    for address in gathered_addresses:
      build_graph.inject_address_closure(address)
      discovered_targets.add(build_graph.get_target(address))

    synthetic_target = cls.create_synthetic_target(options, address_mapper, build_graph, discovered_targets)

    # TODO(mateo): Should probably allow for implementers that do not need to create a new target and return None here.
    if not isinstance(synthetic_target, Target):
      raise ValueError("{}.create_synthetic_target() needs to return a Target subclass".format(cls))
    cls.maybe_add_dependency_edges(build_graph, original_targets, synthetic_target)
    return cls.new_target_roots(build_graph, synthetic_target)
