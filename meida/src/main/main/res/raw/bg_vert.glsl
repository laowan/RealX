precision mediump float;
attribute vec4  a_VertexPos;
attribute vec2  a_VertexUV;
uniform mat4    u_MvpMatrix;
varying vec2    v_TextureUV;
void main(void){
    gl_Position = u_MvpMatrix * a_VertexPos;
    v_TextureUV = vec2(a_VertexUV.x, 1.0-a_VertexUV.y);
}