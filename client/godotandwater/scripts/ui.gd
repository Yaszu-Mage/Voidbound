extends Control
@onready var code_edit: CodeEdit = $Window/Control/Codebox/CodeEdit
@onready var window : Window = $Window
@onready var begin = $Begin
@onready var file_dialog: FileDialog = $FileDialog
@onready var load: Button = $Window/Control/Codebox/Panel/MarginContainer/CenterContainer/HBoxContainer/load
@onready var save: Button = $Window/Control/Codebox/Panel/MarginContainer/CenterContainer/HBoxContainer/save
@onready var specialAddressYes = $"Special Address/HBoxContainer2/Yes"
@onready var specialAddressNo = $"Special Address/HBoxContainer2/No"
@onready var beginSpecialAdress = $"Special Address2/Button"
@onready var subviewPort = $SubViewportContainer/SubViewport
var worldPreload = preload("res://scenes/baseWorld.tscn")
var notificationActive = false
func _ready() -> void:
	print("wtf")
	window.popup()
	#center_camera_on_control()
	begin.pressed.connect(specialAddressAsk)
	specialAddressNo.pressed.connect(start)
	specialAddressYes.pressed.connect(specialAddressQuestion)
	beginSpecialAdress.pressed.connect(startSpecial)
	file_dialog.use_native_dialog = true
	file_dialog.access = FileDialog.ACCESS_FILESYSTEM
	file_dialog.file_mode = FileDialog.FILE_MODE_OPEN_FILE
	file_dialog.filters = PackedStringArray(["*.txt ; Text Files", "*.gd ; GDScript Files"])
	file_dialog.file_selected.connect(processFile)
	load.pressed.connect(openFile)
	save.pressed.connect(saveFile)

func openFile():
	file_dialog.file_mode = FileDialog.FILE_MODE_OPEN_FILE
	file_dialog.popup_centered()
func saveFile():
	file_dialog.file_mode = FileDialog.FILE_MODE_SAVE_FILE
	file_dialog.popup_centered()
func processFile(path :String) -> void:
	if file_dialog.file_mode == FileDialog.FILE_MODE_SAVE_FILE:
		var file := FileAccess.open(path,FileAccess.WRITE)
		if file:
			file.store_string(code_edit.text)
			file.close()
		else:
			push_error("FAILED TO SAVE FILE")
	else:
		var file = FileAccess.open(path,FileAccess.READ)
		if file:
			var content = file.get_as_text()
			code_edit.text = content
			file.close()
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
#@onready var camera: Camera2D = $Window/windowcam
@onready var control_node: Control = $Window/Control

#func center_camera_on_control() -> void:
	## Get the center offset of the Control node
	#var control_center: Vector2 = control_node.global_position + (control_node.size * control_node.scale) / 2.0
	#camera.global_position = control_center



@onready var notificationTitle : RichTextLabel =  $Notification/CenterContainer/HBoxContainer/Title
@onready var notificationBody : RichTextLabel = $Notification/CenterContainer/HBoxContainer/VBoxContainer/Body
@onready var notificationAnimation : AnimationPlayer = $notificationHandler
@onready var console = $Window/Control/Codebox/Console/RichTextLabel
func showNotification(title : String, body : String,time : float) -> void:
	if notificationActive == false:
		notificationActive = true
		notificationBody.text = ""
		notificationTitle.text = ""
		$Notification.size = Vector2(341,84)
		notificationTitle.text = title
		notificationBody.text = body
		notificationAnimation.play("showNotification")
		await get_tree().create_timer(time).timeout
		notificationAnimation.play("hideNotification")
		await notificationAnimation.animation_finished
		notificationActive = false
	else:
		await get_tree().create_timer(10.0).timeout
		showNotification(title,body,time)
func showConsole(value : String) -> void:
	console.text = value
