extends Node3D


@export var websocketUrl = "wss://socket.yaszu.xyz"

var socket = WebSocketPeer.new()
@onready var player = $player

# Call this function whenever you want to send data
func send_json(data: Variant) -> void:
	if socket.get_ready_state() == WebSocketPeer.STATE_OPEN:
		var json_string := JSON.stringify(data)
		socket.send_text(json_string)

# Called when the node enters the scene tree for the first time.
func _ready() -> void:
	var err = socket.connect_to_url(websocketUrl)
	if err == OK:
		print("Connecting to %s..." % websocketUrl)
		await get_tree().create_timer(2).timeout
		print("> Sending initialization packet.")
	else:
		push_error("Unable to connect.")
		set_process(false)


# Called every frame. 'delta' is the elapsed time since the previous frame.
func _process(delta: float) -> void:
	socket.poll()
	var state := socket.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var packet := socket.get_packet()
			if socket.was_string_packet():
				var response_text := packet.get_string_from_utf8()
				print("Received text: ", response_text)
				# Optional: Parse JSON response
				var data = JSON.parse_string(response_text)
				print(typeof(data))
				if data == null:
					print("Non-Json Message: ", response_text)
					continue
				match typeof(data):
					TYPE_DICTIONARY:
						_handle_message(data)
					TYPE_ARRAY:
						_handle_array(data)
		var update = {"type" : "UPDATE","x" : player.global_position.x, "y" : player.global_position.y, "z" : player.global_position.z}
		send_json(update)
		
func _handle_array(data : Array) -> void:
	for entry in data:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		_handle_message(entry)

func _handle_message(data : Dictionary) -> void:
	if not data.has("type"):
		return
	match data["type"]:
		"JOIN":
			if data.has("uuid"):
				player.name = data["uuid"]
				print("UUID ", data["uuid"])
		"UPDATE":
			var givenPosition = Vector3(data["x"],data["y"],data["z"])
			if player.global_position.distance_to(givenPosition) <= 10:
				return
		_:
			print("Unknown message type: ", data["type"])
