extends Control
@onready var window : Window = $Window

func _ready() -> void:
	window.popup()
	center_camera_on_control()

@onready var camera: Camera2D = $Window/windowcam
@onready var control_node: Control = $Window/Control

func center_camera_on_control() -> void:
	# Get the center offset of the Control node
	var control_center: Vector2 = control_node.global_position + (control_node.size * control_node.scale) / 2.0
	camera.global_position = control_center
