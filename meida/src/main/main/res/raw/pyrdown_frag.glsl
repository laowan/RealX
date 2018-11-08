precision mediump float;
varying vec2 v_TextureUV;
uniform sampler2D u_Texture;
const highp float stepX = 0.000925925925925926;
const highp float stepX2 = 0.001851851851851852;
const highp float stepY = 0.0005208333333333333;
const highp float stepY2 = 0.0010416666666666666;
vec3 pyrdown5(sampler2D tex, vec2 uv){
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

	vec3 x1 = texture2D(tex, vec2(minX, maxY)).rgb;
	vec3 x2 = texture2D(tex, vec2(minX+stepX, maxY)).rgb;
	vec3 x3 = texture2D(tex, vec2(uv.x, maxY)).rgb;
	vec3 x4 = texture2D(tex, vec2(maxX-stepX, maxY)).rgb;
	vec3 x5 = texture2D(tex, vec2(maxX, maxY)).rgb;

	vec3 x6 = texture2D(tex, vec2(minX, maxY-stepY)).rgb;
	vec3 x7 = texture2D(tex, vec2(minX+stepX, maxY-stepY)).rgb;
	vec3 x8 = texture2D(tex, vec2(uv.x, maxY-stepY)).rgb;
	vec3 x9 = texture2D(tex, vec2(maxX-stepX, maxY-stepY)).rgb;
	vec3 x10 = texture2D(tex, vec2(maxX, maxY-stepY)).rgb;

	vec3 x11 = texture2D(tex, vec2(minX, uv.y)).rgb;
	vec3 x12 = texture2D(tex, vec2(minX+stepX, uv.y)).rgb;
	vec3 x13 = texture2D(tex, vec2(uv.x, uv.y)).rgb;
	vec3 x14 = texture2D(tex, vec2(maxX-stepX, uv.y)).rgb;
	vec3 x15 = texture2D(tex, vec2(maxX, uv.y)).rgb;

	vec3 x16 = texture2D(tex, vec2(minX, minY+stepY)).rgb;
	vec3 x17 = texture2D(tex, vec2(minX+stepX, minY+stepY)).rgb;
	vec3 x18 = texture2D(tex, vec2(uv.x, minY+stepY)).rgb;
	vec3 x19 = texture2D(tex, vec2(maxX-stepX, minY+stepY)).rgb;
	vec3 x20 = texture2D(tex, vec2(maxX, minY+stepY)).rgb;

    vec3 x21 = texture2D(tex, vec2(minX, minY)).rgb;
    vec3 x22 = texture2D(tex, vec2(minX+stepX, minY)).rgb;
    vec3 x23 = texture2D(tex, vec2(uv.x, minY)).rgb;
    vec3 x24 = texture2D(tex, vec2(maxX-stepX, minY)).rgb;
    vec3 x25 = texture2D(tex, vec2(maxX, minY)).rgb;

    vec3 result =  x1 * 1.0 + x2 * 4.0 + x3 * 7.0 + x4 * 4.0 + x5 * 1.0 + x6 * 4.0 + x7 * 16.0 + x8 * 26.0 + x9 * 16.0 + x10 * 4.0 + x11 * 7.0 + x12 * 26.0 + x13 * 41.0 + x14 * 26.0 + x15 * 7.0 + x16 * 4.0 + x17 * 16.0 + x18 * 26.0 + x19 * 16.0 + x20 * 4.0 + x21 * 1.0 + x22 * 4.0 + x23 * 7.0 + x24 * 4.0 + x25 * 1.0;
    return result/273.0;
}

void main(void){
    //gl_FragColor = texture2D(u_Texture, v_TextureUV);
    gl_FragColor = vec4(pyrdown5(u_Texture, v_TextureUV), 1.0);
}