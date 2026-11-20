extends Node3D


@export var websocketUrl = "wss://socket.yaszu.xyz"

@onready var UI = $"../../.."
@onready var sendButton : Button = $"../../../Window/Control/Codebox/Panel/MarginContainer/CenterContainer/HBoxContainer/send"
@onready var codeEditor : CodeEdit = $"../../../Window/Control/Codebox/CodeEdit"
@export var shell = preload("res://scenes/shellPlayer.tscn")
var socket = WebSocketPeer.new()
@onready var player = $player
var pingCounter = 5

# Call this function whenever you want to send data
func send_json(data: Variant) -> void:
	if socket.get_ready_state() == WebSocketPeer.STATE_OPEN:
		var json_string := JSON.stringify(data)
		socket.send_text(json_string)
	randf_range(0,2.5)
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
	sendButton.pressed.connect(sendCode)


func sendCode():
	var code = codeEditor.text
	var sentCode = {"type" : "CODE", "code" : code}
	send_json(sentCode)
	UI.showNotification("CODE STATUS","Sent Code!",0.5)
	

# Called every frame. 'delta' is the elapsed time since the previous frame.
func _process(delta: float) -> void:
	socket.poll()
	var state := socket.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var packet := socket.get_packet()
			if socket.was_string_packet():
				var response_text := packet.get_string_from_utf8()
				#print("Received text: ", response_text)
				# Optional: Parse JSON response
				var data = JSON.parse_string(response_text)
				if data == null:
					print("Non-Json Message: ", response_text)
					continue
				match typeof(data):
					TYPE_DICTIONARY:
						_handle_message(data)
					TYPE_ARRAY:
						_handle_array(data)
		var update = {"type" : "UPDATE","x" : player.global_position.x, "y" : player.global_position.y, "z" : player.global_position.z}
		if pingCounter >= 10:
			send_json(update)
			pingCounter = 0
		pingCounter += 1
	else:
		if !(state != WebSocketPeer.STATE_CLOSED && state != WebSocketPeer.STATE_CLOSING):
			var err = socket.connect_to_url(websocketUrl)
		
func _handle_array(data : Array) -> void:
	for entry in data:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		_handle_message(entry)

var players = []

func _handle_message(data : Dictionary) -> void:
	if not data.has("type"):
		return
	match data["type"]:
		"JOIN":
			if data.has("uuid"):
				player.name = data["uuid"]
				print("UUID ", data["uuid"])
		"SELF-UPDATE":
			var givenPosition = Vector3(data["x"],data["y"],data["z"])
			if player.global_position.distance_to(givenPosition) <= 10:
				return
			else:
				player.global_position = givenPosition
		"UPDATE":
			var activePlayers = data["players"]
			_handle_array(activePlayers)
		"PLAYER-INFORMATION":
			if player.name == data["uuid"]: return
			if (self.has_node(data["uuid"])):
				var playerInstance = self.get_node(data["uuid"])
				var givenPosition = Vector3(data["x"],data["y"],data["z"])
				playerInstance.global_position = givenPosition
			else:
				var shellInstance = shell.instantiate()
				shellInstance.name = data["uuid"]
				add_child(shellInstance)
			print(data)
		"CODE-OUTPUT":
			UI.showConsole(data["output"])
			#cool code works
			print(data["code"])
			var script : GDScript = create_script_from_string(str(data["code"]))
			if script:
				var instance = script.new()
				instance.name = "script"
				if (player.has_node("script")):
					player.get_node("script").free()
				get_node(data["uuid"]).add_child(instance)
		"CODE-ERROR":
			print(data)
			UI.showNotification("CODE ERROR", data["output"],40)
		"CODE-BROADCAST":
			pass
			var script : GDScript = create_script_from_string(str(data["code"]))
			var uuid : String = str(data["uuid"])
			if script:
				var instance = script.new()
				instance.name = "script"
				var playerNode : CharacterBody3D = get_node(uuid)
				if playerNode:
					if playerNode.has_node("script"):
						playerNode.get_node("script").free()
					get_node(data["uuid"]).add_child(instance)
		_:
			print("Unknown message type: ", data["type"])


func create_script_from_string(codeString: String) -> GDScript:
	var script := GDScript.new()
	script.source_code = codeString
	var err := script.reload()
	if err != OK:
		UI.showNotification("CODE ERROR", err,40)
		return null
	return script
