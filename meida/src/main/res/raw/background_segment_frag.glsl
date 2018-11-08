precision mediump float;
varying vec2 v_TextureUV;
uniform sampler2D u_TextureBG;
uniform sampler2D u_Texture;
void main(void){
    float segR = texture2D(u_TextureBG, v_TextureUV).r;
    if(segR > 0.5){
        gl_FragColor = texture2D(u_Texture, v_TextureUV);
    }else{
        gl_FragColor = vec4(1.0, 1.0, 1.0, 0.0);
    }
}