extends SceneTree

func _init():
	# Load and instantiate your Node script
	var player = load("res://player.tscn").instantiate()
	var blankWorld = load("res://blankWorld.tscn").instantiate()
	root.add_child(blankWorld)
	blankWorld.add_child(player)
	_snapshot(root)                      # BEFORE adding the script
	var node_instance = load("res://script.gd").new()
	player.add_child(node_instance)
	await create_timer(2.5).timeout
	node_instance.free()
	_lockdown(root)
	var blankWorldCopy = load("res://blankWorld.tscn").instantiate()
	root.add_child(blankWorldCopy)
	blankWorldCopy.add_child(player.duplicate())
	if is_uneditable_unchanged(blankWorld, blankWorldCopy):
		if player.get_node("cambod").get_node("Camera3D").global_position.distance_to(player.global_position) >= 5:
			push_error("ERROR THE PLAYER CAMERA HAS MOVED TOO FAR FROM THE ORIGINAL POSITION! THIS IS NOT ALLOWED!")
		quit()
	else:
		#error out
		push_error("ERROR THE UNEDITABLE PORTIONS WORLD HAS BEEN MODIFIED! THIS IS NOT ALLOWED!")
	quit()

const UNEDITABLE_NAME := "uneditable"
var _allowed := {}   # instance_id -> original script

func _snapshot(n: Node):
	_allowed[n.get_instance_id()] = n.get_script()
	for c in n.get_children():
		_snapshot(c)

func _lockdown(n: Node):
	for c in n.get_children():
		if not _allowed.has(c.get_instance_id()):
			n.remove_child(c)
			c.free()            # added by the script
		else:
			if c.get_script() != _allowed[c.get_instance_id()]:
				c.set_script(_allowed[c.get_instance_id()])  # swapped by the script
			_lockdown(c)
	n.process_mode = Node.PROCESS_MODE_DISABLED
const TRANSFORM_PROPS := [
	"position", "rotation", "rotation_degrees", "scale", "skew",
	"transform", "quaternion", "basis",
	"global_position", "global_rotation", "global_transform",
]
const MAX_RESOURCE_DEPTH := 8
const APPROX_TYPES := [
	TYPE_VECTOR2, TYPE_VECTOR3, TYPE_VECTOR4, TYPE_RECT2, TYPE_QUATERNION,
	TYPE_COLOR, TYPE_PLANE, TYPE_AABB, TYPE_BASIS, TYPE_TRANSFORM2D, TYPE_TRANSFORM3D,
]

# Prints the first thing that differs. Turn off when you're done debugging.
var debug_mismatch := true


## Returns true if both nodes exist and the child named "uneditable" (plus everything
## inside it) is the same in both. Everything outside that child is ignored.
func is_uneditable_unchanged(original: Node, copy: Node, ignore_transform: bool = false,
		extra_skip: Array = []) -> bool:
	if original == null or copy == null:
		return _fail("", "original or copy doesn't exist")

	# owned = false so it also works on nodes that were duplicated/built in code (no owner set)
	var locked_a := original.find_child(UNEDITABLE_NAME, true, false)
	var locked_b := copy.find_child(UNEDITABLE_NAME, true, false)
	if locked_a == null or locked_b == null:
		return _fail("", "'%s' missing (original: %s, copy: %s)"
				% [UNEDITABLE_NAME, locked_a != null, locked_b != null])

	return are_nodes_identical(locked_a, locked_b, ignore_transform, true, extra_skip)


func are_nodes_identical(node_a: Node, node_b: Node, ignore_transform: bool = false,
		ignore_names: bool = true, extra_skip: Array = [], _path: String = "") -> bool:
	if node_a == null or node_b == null:
		if node_a != node_b:
			return _fail(_path, "one node is null and the other isn't")
		return true

	var here := _path + "/" + String(node_a.name)

	if node_a.get_class() != node_b.get_class():
		return _fail(here, "class %s vs %s" % [node_a.get_class(), node_b.get_class()])
	if node_a.get_script() != node_b.get_script():
		return _fail(here, "script %s vs %s" % [node_a.get_script(), node_b.get_script()])
	if not ignore_names and node_a.name != node_b.name:
		return _fail(here, "name '%s' vs '%s'" % [node_a.name, node_b.name])
	if node_a.get_child_count() != node_b.get_child_count():
		return _fail(here, "child count %d vs %d" % [node_a.get_child_count(), node_b.get_child_count()])

	var skip: Array = ["script"]
	skip.append_array(extra_skip)
	if ignore_transform:
		skip.append_array(TRANSFORM_PROPS)
	if not _props_equal(node_a, node_b, skip, 0, here):
		return false

	for i in node_a.get_child_count():
		if not are_nodes_identical(node_a.get_child(i), node_b.get_child(i),
				ignore_transform, ignore_names, extra_skip, here):
			return false
	return true


func _fail(where: String, msg: String) -> bool:
	if debug_mismatch:
		print("[identical] MISMATCH at ", where, ": ", msg)
	return false


func _props_equal(a: Object, b: Object, skip: Array, depth: int, where: String) -> bool:
	var names_b := {}
	for p in b.get_property_list():
		names_b[String(p.name)] = true

	for p in a.get_property_list():
		if (p.usage & PROPERTY_USAGE_STORAGE) == 0:
			continue
		var prop_name := String(p.name)
		if prop_name in skip:
			continue
		if not names_b.has(prop_name):
			return _fail(where, "property '%s' missing on the other object" % prop_name)
		var va: Variant = a.get(prop_name)
		var vb: Variant = b.get(prop_name)
		if not _values_equal(va, vb, depth, where + ":" + prop_name):
			return _fail(where, "property '%s': %s vs %s" % [prop_name, str(va), str(vb)])
	return true


func _values_equal(a: Variant, b: Variant, depth: int, where: String) -> bool:
	var ta := typeof(a)
	var tb := typeof(b)

	if ta != tb:
		if (ta == TYPE_INT or ta == TYPE_FLOAT) and (tb == TYPE_INT or tb == TYPE_FLOAT):
			return is_equal_approx(float(a), float(b))
		return false

	if ta == TYPE_FLOAT:
		return is_equal_approx(a, b)
	if ta in APPROX_TYPES:
		return a.is_equal_approx(b)

	match ta:
		TYPE_ARRAY:
			if a.size() != b.size():
				return false
			for i in a.size():
				if not _values_equal(a[i], b[i], depth, where + "[%d]" % i):
					return false
			return true
		TYPE_DICTIONARY:
			if a.size() != b.size():
				return false
			for k in a:
				if not b.has(k) or not _values_equal(a[k], b[k], depth, where + "[%s]" % str(k)):
					return false
			return true
		TYPE_OBJECT:
			if a == b:
				return true
			if a is Resource and b is Resource:
				return _resources_equal(a, b, depth + 1, where)
			return false
		_:
			return a == b


func _resources_equal(a: Resource, b: Resource, depth: int, where: String) -> bool:
	if depth > MAX_RESOURCE_DEPTH:
		return a == b
	if a.get_class() != b.get_class() or a.get_script() != b.get_script():
		return false
	var a_external := a.resource_path != "" and not a.resource_path.contains("::")
	var b_external := b.resource_path != "" and not b.resource_path.contains("::")
	if a_external or b_external:
		return a.resource_path == b.resource_path
	return _props_equal(a, b, ["script", "resource_path"], depth, where)
