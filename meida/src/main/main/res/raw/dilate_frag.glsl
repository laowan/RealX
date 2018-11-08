precision mediump float;
varying vec2 v_TextureUV;
uniform sampler2D u_Texture;
const highp float stepX = 0.000925925925925926;
const highp float stepX2 = 0.001851851851851852;
const highp float stepY = 0.0005208333333333333;
const highp float stepY2 = 0.0010416666666666666;
vec4 dilate3(sampler2D tex, vec2 uv){
    highp float minX = uv.x-stepX;
    highp float maxX = uv.x+stepX;
    highp float minY = uv.y-stepY;
    highp float maxY = uv.y+stepY;
    if(minX<0.0){
        minX = 0.0;
    }
    if(maxX>1.0){
        maxX = 1.0;
    }
    if(minY<0.0){
        minY = 0.0;
    }
    if(maxY>1.0){
        maxY = 1.0;
    }
    if(
        texture2D(tex, vec2(minX, maxY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, maxY)).r>0.5 ||
        texture2D(tex, vec2(maxX, maxY)).r>0.5 ||

        texture2D(tex, vec2(minX, uv.y)).r>0.5 ||
        texture2D(tex, vec2(uv.x, uv.y)).r>0.5 ||
        texture2D(tex, vec2(maxX, uv.y)).r>0.5 ||

        texture2D(tex, vec2(minX, minY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, minY)).r>0.5 ||
        texture2D(tex, vec2(maxX, minY)).r>0.5
    ){
        return vec4(1.0,1.0,1.0,1.0);
    }else{
        return vec4(0.0,0.0,0.0,0.0);
    }
}

vec4 dilate5(sampler2D tex, vec2 uv){
    highp float minX = uv.x-stepX2;
    highp float maxX = uv.x+stepX2;
    highp float minY = uv.y-stepY2;
    highp float maxY = uv.y+stepY2;
    if(minX<0.0){
        minX = 0.0;
    }
    if(maxX>1.0){
        maxX = 1.0;
    }
    if(minY<0.0){
        minY = 0.0;
    }
    if(maxY>1.0){
        maxY = 1.0;
    }
    if(
        texture2D(tex, vec2(minX, maxY)).r>0.5 ||
        texture2D(tex, vec2(minX+stepX, maxY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, maxY)).r>0.5 ||
        texture2D(tex, vec2(maxX-stepX, maxY)).r>0.5 ||
        texture2D(tex, vec2(maxX, maxY)).r>0.5 ||

        texture2D(tex, vec2(minX, maxY-stepY)).r>0.5 ||
        texture2D(tex, vec2(minX+stepX, maxY-stepY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, maxY-stepY)).r>0.5 ||
        texture2D(tex, vec2(maxX-stepX, maxY-stepY)).r>0.5 ||
        texture2D(tex, vec2(maxX, maxY-stepY)).r>0.5 ||

        texture2D(tex, vec2(minX, uv.y)).r>0.5 ||
        texture2D(tex, vec2(minX+stepX, uv.y)).r>0.5 ||
        texture2D(tex, vec2(uv.x, uv.y)).r>0.5 ||
        texture2D(tex, vec2(maxX-stepX, uv.y)).r>0.5 ||
        texture2D(tex, vec2(maxX, uv.y)).r>0.5 ||

        texture2D(tex, vec2(minX, minY+stepY)).r>0.5 ||
        texture2D(tex, vec2(minX+stepX, minY+stepY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, minY+stepY)).r>0.5 ||
        texture2D(tex, vec2(maxX-stepX, minY+stepY)).r>0.5 ||
        texture2D(tex, vec2(maxX, minY+stepY)).r>0.5 ||

        texture2D(tex, vec2(minX, minY)).r>0.5 ||
        texture2D(tex, vec2(minX+stepX, minY)).r>0.5 ||
        texture2D(tex, vec2(uv.x, minY)).r>0.5 ||
        texture2D(tex, vec2(maxX-stepX, minY)).r>0.5 ||
        texture2D(tex, vec2(maxX, minY)).r>0.5
    ){
        return vec4(1.0,1.0,1.0,1.0);
    }else{
        return vec4(0.0,0.0,0.0,0.0);
    }
}
void main(void){
    gl_FragColor = dilate5(u_Texture, v_TextureUV);
}