precision mediump float;
varying vec2 v_TextureUV;
uniform sampler2D u_Texture;
void main(void){
    gl_FragColor = texture2D(u_Texture, v_TextureUV);
}