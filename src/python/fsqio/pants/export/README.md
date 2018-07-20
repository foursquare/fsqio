# Custom Export Task
This export task overrides the pants base task to accomplish two main purposes:
1. autogenerate the stubs used by IntelliJ to ease indexing pains
1. Filter out actual generated code for same reason

# Longer term approach (AKA TODO)
There are two possible approaches to the root cause (massive amounts of generated code chokes IntelliJ)
1. Allowing stub registration and a hot-swap from generated code to a generated stub within pants, submitting this product enhancement into mainline of pants and removing this customization
1. Reworking spindle code to interface --> implementation and programming to the interfaces rather than the concrete implementations. This is non-trivial and potentially impossible with the size of our code base, but it likely the "Most Correct" way of dealing with this issue. 

# Backing this customization out:
* Remove export reference from the pants.ini and pants-internal.ini
* Remove foursquare.web/src/python/fsqio/pants/export
* If a middle ground is required (keeping customization but refactoring it) the export_filtered.py file has TODO markings where the original code was altered. 

