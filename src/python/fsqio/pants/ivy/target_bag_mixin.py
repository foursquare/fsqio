# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.base.build_environment import get_buildroot
from pants.base.payload import Payload
from pants.base.specs import DescendantAddresses, SiblingAddresses
from pants.build_graph.address import Address
from pants.build_graph.target import Target


class TargetBagMixin(object):
  """Task Mixin that will scan configured paths and inject discovered targets into the build_graph."""

  # Override these at Task level as needed- these defaults are pretty bad.
  SYNTHETIC_TARGET_PATH = get_buildroot()

  # Implementing classes that need overrides are offered a few basic levers:
  #  - inject_synthetic_target - allows configuring the synthetic target bag.
  #  - maybe_add_dependency_edges - can add further dependency edges as needed.
  #  - new_target_roots - Can choose to expand the original target_roots or redefine altogether.

  @classmethod
  def prepare(cls, options, round_manager):
    super(TargetBagMixin, cls).prepare(options, round_manager)

  # I am not including these options in any fingerprint bc all that matters is the relationship between targets.
  @classmethod
  def register_options(cls, register):
    super(TargetBagMixin, cls).register_options(register)
    register(
      '--spec-paths',
      type=list,
      default=[],
      advanced=True,
      help='Gather target addresses found in this directory, non-recursively.'
    ),
    register(
      '--spec-roots',
      type=list,
      advanced=True,
      default=['3rdparty'],
      help='Gather target addresses found when recursively scanning this path.'
    ),
    # I could not find a sensible way to include the buildroot in the other options, so fell back to a yes/no.
    register(
      '--include-buildroot',
      type=bool,
      advanced=True,
      default=False,
      help='Include targets defined in the buildroot, non-recursive.'
    ),

  @classmethod
  def gathered_target_type_aliases(cls):
    """Return a tuple of target type_alias to collect.

    A tuple of target type_alias string that indicate the addressable to scan and collect from the BUILD files
    e.g. ("jar_library", "python_library").
    :rtype tuple:
    """
    raise NotImplementedError

  @classmethod
  def injected_target_name(cls):
    """Return a unique string that will be used to identify the created synthetic bag target."""
    raise NotImplementedError

  @classmethod
  def get_synthetic_address(cls):
    # TODO(mateo): Add an error catch in case the address is already in the graph (meaning two implementing tasks),
    # which means one or both must also override the constants used for the addressing.
    return Address(spec_path='', target_name=cls.injected_target_name())

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
  def add_payload_fields(cls, build_graph, addresses, payload):
    """Add fields to a payload

    :param `pants.build_graph.BuildGraph` build_graph: Populated build_graph instance
    :param collection[`pants.build_graph.Addresses`] addresses: Collection of addresses.
      not in the context of any target_root.
    :param `pants.base.Payload` payload: Payload to be updated.
    :returns payload:
    :rtype pants.base.Payload:
    """
    return payload

  @classmethod
  def create_synthetic_target(cls, options, address_mapper, build_graph, matching_addresses):
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

    payload = cls.add_payload_fields(build_graph, matching_addresses, Payload())
    synthetic_target = cls.inject_synthetic_target(
      build_graph,
      cls.get_synthetic_address(),
      payload=payload,
      dependencies=matching_addresses,
    )
    return synthetic_target

  @classmethod
  def alternate_target_roots(cls, options, address_mapper, build_graph):
    discovered_targets = set()
    original_targets = build_graph.targets()

    # The address macros return OrderedSets, which is not considered here. I believe that to be fine but FYI.
    buildroot = {SiblingAddresses('')} if options.get('include_buildroot') else set()
    paths_checked = {SiblingAddresses(t) for t in options.get('spec_paths')}
    dirs_checked = {DescendantAddresses(t) for t in options.get('spec_roots')}
    spec_sets = buildroot | paths_checked | dirs_checked

    all_found_addresses = address_mapper.scan_specs(spec_sets)
    matching_addresses = set()

    for address in all_found_addresses:

      # TODO(mateo): This used to be able to get this info without hydrating the target.
      target = build_graph.resolve_address(address)
      if target.type_alias in tuple(cls.gathered_target_type_aliases()):
        matching_addresses.add(address)
        build_graph.inject_address_closure(address)
    synthetic_target = cls.create_synthetic_target(options, address_mapper, build_graph, matching_addresses)

    # TODO(mateo): Should probably allow for implementers that do not need to create a new target and return None here.
    if not isinstance(synthetic_target, Target):
      raise ValueError("{}.create_synthetic_target() needs to return a Target subclass".format(cls))
    cls.maybe_add_dependency_edges(build_graph, original_targets, synthetic_target)
    return cls.new_target_roots(build_graph, synthetic_target)
