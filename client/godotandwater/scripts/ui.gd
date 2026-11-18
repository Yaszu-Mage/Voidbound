extends Control
@onready var window : Window = $Window
@onready var begin = $Begin

@onready var specialAddressYes = $"Special Address/HBoxContainer2/Yes"
@onready var specialAddressNo = $"Special Address/HBoxContainer2/No"
@onready var beginSpecialAdress = $"Special Address2/Button"
@onready var subviewPort = $SubViewportContainer/SubViewport
var worldPreload = preload("res://scenes/baseWorld.tscn")

func _ready() -> void:
	print("wtf")
	window.popup()
	center_camera_on_control()
	begin.pressed.connect(specialAddressAsk)
	specialAddressNo.pressed.connect(start)
	specialAddressYes.pressed.connect(specialAddressQuestion)
	beginSpecialAdress.pressed.connect(startSpecial)

func specialAddressQuestion() -> void:
	specialAddressYes.get_parent().visible = false
	$"Special Address2".visible = true

func specialAddressAsk() -> void:
	begin.visible = false
	$"Special Address".visible = true

func start() -> void:
	if !subviewPort.has_node("world"):
		var worldInstance = worldPreload.instantiate()
		subviewPort.add_child(worldInstance)
		$SubViewportContainer.visible = true
		cleanUp()

func cleanUp() -> void:
	if $Panel != null:
		$Panel.queue_free()
	if $"Special Address2" != null:
		$"Special Address2".queue_free()
	if $Begin != null:
		$Begin.queue_free()
func startSpecial() -> void:
	if !subviewPort.has_node("world"):
		var worldInstance = worldPreload.instantiate()
		worldInstance.websocketUrl = $"Special Address/RichTextLabel".text
		subviewPort.add_child(worldInstance)
		$SubViewportContainer.visible = true
		cleanUp()
@onready var camera: Camera2D = $Window/windowcam
@onready var control_node: Control = $Window/Control

func center_camera_on_control() -> void:
	# Get the center offset of the Control node
	var control_center: Vector2 = control_node.global_position + (control_node.size * control_node.scale) / 2.0
	camera.global_position = control_center
