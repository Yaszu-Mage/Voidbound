extends SceneTree

func _init():
    # Load and instantiate your Node script
    var MyNodeScript = load("res://script.gd")
    var node_instance = MyNodeScript.new()

    # Add it to the root so _ready() and _process() trigger
    root.add_child(node_instance)

    # Optional: Call a specific function directly
    # node_instance.my_custom_function()

    # Quit the engine when done (omit if waiting for signals/timers)
    quit()
