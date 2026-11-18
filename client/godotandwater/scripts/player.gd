extends CharacterBody3D

@onready var checker : MeshInstance3D = $checker
const SPEED = 5.0
const JUMP_VELOCITY = 4.5
@onready var camera = $cambod/Camera3D
func _ready() -> void:
	self.global_position = Vector3(randi_range(0,16),self.global_position.y,randi_range(0,16))

func _physics_process(delta: float) -> void:
	# Add the gravity.
	checker.global_position.y = 0
	var direction = Vector2.ZERO
	if not is_on_floor():
			velocity += get_gravity() * delta
	if (Input.is_action_just_pressed("escape")):
			if (Input.mouse_mode == Input.MOUSE_MODE_CAPTURED):
				Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
			else:
				Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	move_and_slide()
	if Input.mouse_mode != Input.MOUSE_MODE_CAPTURED:
		return
	else:
		
		# Handle jump.
		if Input.is_action_just_pressed("ui_accept") and is_on_floor():
			velocity.y = JUMP_VELOCITY

		# Get the input direction and handle the movement/deceleration.
		# As good practice, you should replace UI actions with custom gameplay actions.
		var input_dir := Input.get_vector("left", "right", "forward", "back")
		direction = ($cambod.transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()
	if direction:
			velocity.x = direction.x * SPEED
			velocity.z = direction.z * SPEED
	else:
			velocity.x = move_toward(velocity.x, 0, SPEED)
			velocity.z = move_toward(velocity.z, 0, SPEED)
	move_and_slide()

const sense = 0.001
const lowestRot = 70
const highestRot = 70

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		$cambod.rotate_y(-event.relative.x * sense)
		camera.rotate_x(-event.relative.y * sense)
		camera.rotation.x = clampf(camera.rotation.x, -deg_to_rad(lowestRot), deg_to_rad(highestRot))
