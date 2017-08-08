## Formatting

### File template
Consider file `thrift_demo.thrift` below

```
namespace java io.fsq.XX.YY.gen

include "io/fsq/ids/ids.thrift"
include "io/fsq/common/types/types.thrift"

enum DemoEnum {
  firstValue = 0
  secondValue = 1
}

const i32 DemoConstant1 = 123
const DemoEnum DemoConstant2 = DemoEnum.firstValue

struct DemoStruct {
  1: optional ids.VenueId venueId (wire_name="_id")
  2: optional types.DateTime date (wire_name="dt", builder_required="true")
  4: optional i32 count (wire_name="c")
  6: optional ids.UserId userId (wire_name="u")
} (
  primary_key="venueId"
  foreign_key="userId"
  index="userId:asc"
  mongo_collection="demo"
  mongo_identifier="uva"
  retired_ids="3,5"
  retired_wire_names="a,b"
)
```

### Namespace and location
 * **Do not** add the python namespace unless you are **sure** you need it.
 * The namespace should be the path under `src/thrift/` plus `.gen`
   * test namespaces should end with `.test.gen`
 * The directory structure should exactly correspond to the namespace
    * `namespace java io.fsq.foo.gen` defined in `src/thrift/io/fsq/foo/gen/bar.thrift`
    * `namespace java io.fsq.foo.test.gen` defined in `test/thrift/io/fsq/foo/test/gen/bar.thrift`
 * This exactly matches the generated package structure
 * This is not always correctly done but should be followed as of this wiki page.

### Naming
 * Enums/Consts/Structs/Services are PascalCase
 * Members of those should be camelCase, outside of some cases which are kept inline with corresponding lift models
 * Annotations are c_style_casing
 * Filenames are c_style_casing

### Whitespace

* Follow the same general rules as scala (2 space indents, 120 chars, no trailing whitespace, blank lines between structs, etc)
* No spaces around the = for annotations

### Comments

Thrift also follows the same rules as scala (`//...` for single line, `/* */` for multiline)

### Field definitions
* Field ids start at 1 and increment from there.
* All structure fields must specify `optional`. You should never add a `required` field and be very careful with existing `required` fields. All fields in a struct saved to mongo (including the `_id`) should be optional. If you want to ensure a field as part of an object, use the `builder_requred="true"` annotation. Never change an optional field to be required
* Prefer our typesafe ids (eg `ids.VenueId` from `src/thrift/io/fsq/common/types/types.thrift`) to a standard `ObjectId` or creating your own.
* Fields are to be immutable after their creation, with the possible exception of some annotations. **Never change a field's id or type.**
* All structs being saved in mongo should have a `wire_name` annotation on every field. This is unnecessary for ones only being used elsewhere.
* Maps being stored in mongo must be string keyed because that is all that BSON supports.

### Structures
* Fields should be ordered by their field ids
* Whenever you remove a field, you must add the id to the `retired_ids` annotation and the wire name to the `retired_wire_names` annotation for mongo structs (if the field didn't have a defined `wire_name`, it was stored using the field's name)

### Annotations

Here is a incomplete list of important annotations you will probably run across or need. An important gotcha: when an annotation refers to a field it requires the fields thrift name not the wire_name.

* Anything going into mongo requires
  * `mongo_collection="bar"` The name of the collection to read/write from.
  * `mongo_identifier="foo"` The name of the mongo to read/write from.
  * `primary_key="fieldName"` The primary key of the collection (almost always whatever has the `wire_name="_id"`)
* Other mongo fields include
  * `foreign_key="fieldName"` Name any foreign keys with this annotation.
  * `index="fieldName:[asc,desc,hashed][,...]"` All indexes should be annotated on the record.
* delta service annotations
  * `soft_delete_field="deleted"` names the field to use as a tombstone to mark a record deleted. records with this annotation should *not* be deleted directly from mongo or bad things will happen.
  * `ttl_field="lastModifiedTimestamp"` is the name of the timestamp field. this is generally an index in mongo used to prune old records
  * `meta_extends="io.fsq.spindle.types.MongoDisallowed"` allows you to extend the MetaRecord class with arbitrary classes. in this specific case we use it to create a phantom type that disallows using our services.db directly with the record.
* Retired fields
  * `retired_ids="2,11,14"` Add or add to this annotation when you remove a field from a record
  * `retired_wire_names="latestNonSpecialUpdate,lu,c"` Add the `wire_name` or the field name to this annotation when you remove a field
* Proxies
  * `generate_proxy="1"` Creates a Proxy class that passes all methods through to an underlying Thrift object while allowing you to add your own methods. Ex: if you have a Foo Thrift model, this would generate a FooProxy class that you can inherit from while overriding `underlying: Foo`. It has all the methods that the original Foo model has (like `whateverFieldOption`, `whateverFieldOrThrow`, etc.), but by inheriting from the Proxy you can add your own extra methods.
* I don't remember what these do??
  * `generate_lift_adapter="1"`
  * `messageset_id="1359558803000"`

### Enum
* When deprecating an enum value, don't remove it. Rather, rename it from `foo` to `DEPRECATED_foo`. This way existing data with this enum will still make sense.
* `string_value`: useful when converting from scala enum and when the output ultimately ends up in an api response
* In general, avoid renaming enums since they might get denormalized to their name in redshift or elsewhere.

### Const
* Spindle gathers all constants into a single codegen-ed object whose name is derived by converting the thrift filename from c_style_casing to PascalCase and appending `Constants`. In the example above, the object is `com.foursquare.XX.YY.gen.ThriftDemoConstants`.
