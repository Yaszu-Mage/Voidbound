shader_type spatial;


uniform vec2 grid_size = vec2(100, 100);
uniform vec3 color_1 : source_color = vec3(0.9, 0.9, 0.9);
uniform vec3 color_2 : source_color = vec3(0.2, 0.2, 0.2);
uniform vec4 base_color : source_color = vec4(0.2, 0.2, 0.2, 1.0);
uniform sampler2D gradient;
uniform sampler2D opacity_mask;

void fragment() {
	vec2 grid_pos = floor(UV * grid_size + (CAMERA_POSITION_WORLD.xz));
	vec4 color = texture(gradient, UV);
	// Sum the X and Y grid positions and check if the result is odd (1.0) or even (0.0)
	float checker = mod(grid_pos.x + grid_pos.y, 2.0);
    float mask = texture(opacity_mask, UV).r;
	// Blend between the two colors and apply to ALBEDO
	ALBEDO = mix(mix(color_1, color_2, checker),color.rgb,mask);
	ALPHA = mask;
}