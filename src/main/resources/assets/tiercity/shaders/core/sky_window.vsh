#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 clipPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    clipPos = gl_Position;
}
