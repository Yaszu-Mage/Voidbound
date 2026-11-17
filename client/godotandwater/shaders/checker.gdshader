shader_type spatial;


uniform vec2 grid_size = vec2(12.0, 12.0);
uniform vec3 color_1 : source_color = vec3(0.9, 0.9, 0.9);
uniform vec3 color_2 : source_color = vec3(0.2, 0.2, 0.2);
uniform vec4 base_color : source_color = vec4(0.2, 0.2, 0.2, 1.0);
uniform vec4 edge_color : source_color = vec4(1.0, 0.5, 0.0, 1.0);
uniform float border_width : hint_range(0.001, 0.5) = 0.05;

void fragment() {
	// Scale the UVs and snap them to whole grid numbers
	vec2 dist = min(UV, 1.0 - UV);
	float minDist = min(dist.x,distfloat
	
	float is_edge = 1.0 - step(border_width, min_dist)
	vec2 grid_pos = floor(UV * grid_size + CAMERA_POSITION_WORLD.xy);
	
	// Sum the X and Y grid positions and check if the result is odd (1.0) or even (0.0)
	float checker = mod(grid_pos.x + grid_pos.y, 2.0);
	
	// Blend between the two colors and apply to ALBEDO
	ALBEDO = mix(color_1, color_2, checker);
}