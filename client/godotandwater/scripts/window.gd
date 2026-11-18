extends Window
@onready var control = $Control

# Called when the node enters the scene tree for the first time.
func _ready() -> void:
	size_changed.connect(_on_window_resized)
	_on_window_resized()


# Called every frame. 'delta' is the elapsed time since the previous frame.
func _on_window_resized() -> void:
	control.size = size
