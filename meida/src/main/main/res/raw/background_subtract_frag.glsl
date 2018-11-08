precision mediump float;
varying vec2 v_TextureUV;
uniform sampler2D u_TextureBG;
uniform sampler2D u_Texture;
uniform float u_Threshold;
const float PI = 3.1415926535898;
const float R = 100.0;
//R*cost(angle/180*PI);
const float h = 70.7106781186548;
//R*sin(angle/180*PI);
const float r = 50.0;

const float stepX = 1.0/1080.0;
const float stepY = 1.0/1920.0;

const float hsvDis = 25.1;
const float grayDis = 0.20;

vec3 rgb2hsv(vec3 rgb){
	float vMax = max(max(rgb.r, rgb.g), rgb.b);
	float vMin = min(min(rgb.r, rgb.g), rgb.b);
	float vSubtract = vMax - vMin;
	vec3 result;
	result.b = vMax;
	if(vSubtract == 0.0){
		result.g = 0.0;
		result.r = 0.0;
	}else{
		result.g = vSubtract/vMax;
		if(vMax == rgb.r){
			if(rgb.r >= rgb.b){
				result.r = 60.0*(rgb.r - rgb.b)/vSubtract;
			}else{
				result.r = 60.0*(rgb.r - rgb.b)/vSubtract + 360.0;
			}
		}else if(vMax == rgb.g){
			result.r = 60.0*(rgb.r - rgb.b)/vSubtract + 120.0;
		}else{
			result.r = 60.0*(rgb.r - rgb.b)/vSubtract + 240.0;
		}
	}
	return result;
}

float distanceOf(vec3 sHSV, vec3 dHSV){
	vec3 src = vec3(r * sHSV.b * sHSV.g * cos(sHSV.r / 180.0 * PI), r * sHSV.b * sHSV.g * sin(sHSV.r / 180.0 * PI), h * (1.0 - sHSV.b));
	vec3 dst = vec3(r * dHSV.b * dHSV.g * cos(dHSV.r / 180.0 * PI), r * dHSV.b * dHSV.g * sin(sHSV.r / 180.0 * PI), h * (1.0 - dHSV.b));
	return abs(distance(src, dst));
}

vec3 midFilter(sampler2D tex, vec2 uv){
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
	vec3 x1 = texture2D(tex, vec2(minX, maxY)).rgb; vec3 x2 = texture2D(tex, vec2(uv.x, maxY)).rgb; vec3 x3 = texture2D(tex, vec2(maxX, maxY)).rgb;
	vec3 x4 = texture2D(tex, vec2(minX, uv.y)).rgb; vec3 x5 = texture2D(tex, vec2(uv.x, uv.y)).rgb; vec3 x6 = texture2D(tex, vec2(maxX, uv.y)).rgb;
	vec3 x7 = texture2D(tex, vec2(minX, minY)).rgb; vec3 x8 = texture2D(tex, vec2(uv.x, minY)).rgb; vec3 x9 = texture2D(tex, vec2(maxX, minY)).rgb;
	return vec3(x1+x2+x3+x4+x5+x6+x7+x8+x9)/9.0;
}
vec3 gsFilter(sampler2D tex, vec2 uv){
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
	vec3 x1 = texture2D(tex, vec2(minX, maxY)).rgb; vec3 x2 = texture2D(tex, vec2(uv.x, maxY)).rgb; vec3 x3 = texture2D(tex, vec2(maxX, maxY)).rgb;
	vec3 x4 = texture2D(tex, vec2(minX, uv.y)).rgb; vec3 x5 = texture2D(tex, vec2(uv.x, uv.y)).rgb; vec3 x6 = texture2D(tex, vec2(maxX, uv.y)).rgb;
	vec3 x7 = texture2D(tex, vec2(minX, minY)).rgb; vec3 x8 = texture2D(tex, vec2(uv.x, minY)).rgb; vec3 x9 = texture2D(tex, vec2(maxX, minY)).rgb;
	return x1*0.0947416 + x2*0.118318 + x3*0.0947416 + x4*0.118318 + x5*0.147761 + x6*0.118318 + x7*0.0947416 + x8*0.118318 + x9*0.0947416;
}
// ||
//        (rgb.r > 0.7843137254901961 && rgb.g > 0.8235294117647058 && rgb.b > 0.6666666666666666 &&
//        (rgb.r - rgb.b) <= 0.058823529411764705 && rgb.r > rgb.b &&
//        rgb.g > rgb.b)
bool isSkinRGB(sampler2D tex, vec2 uv){
    vec3 rgb = texture2D(tex, uv).rgb;
    if((rgb.r > 0.37254901960784314 && rgb.g > 0.1568627450980392 && rgb.b > 0.0784313725490196 &&
        (rgb.r - rgb.b) > 0.058823529411764705 && (rgb.r - rgb.g) > 0.058823529411764705)){
        return true;
        }
    return false;
}
void main(void){
	//vec2 v_TextureBGUV = vec2(v_TextureUV.x, 1.0-v_TextureUV.y);
    vec3 vTextureResult = texture2D(u_Texture, v_TextureUV).rgb;
//	vec3 vTextureBG = midFilter(u_TextureBG, v_TextureUV);
//	vec3 vTexture = midFilter(u_Texture, v_TextureUV);
	vec3 vTextureBG = gsFilter(u_TextureBG, v_TextureUV);
	vec3 vTexture = gsFilter(u_Texture, v_TextureUV);
//	vec3 vTextureBG = texture2D(u_TextureBG, v_TextureUV).rgb;
//	vec3 vTexture = texture2D(u_Texture, v_TextureUV).rgb;
	vec3 hsvBG = rgb2hsv(vTextureBG);
	vec3 hsv = rgb2hsv(vTexture);
    float disHSV = distanceOf(hsvBG, hsv);
	float grayBG = vTextureBG.r * 0.299 + vTextureBG.g * 0.587 + vTextureBG.b * 0.114;
	float gray = vTexture.r * 0.299 + vTexture.g * 0.587 + vTexture.b * 0.114;
	float dis = abs(grayBG - gray);
//    if(disHSV<hsvDis){
//		if(dis<grayDis){
//			if(abs(vTexture.r-vTextureBG.r)<u_Threshold && abs(vTexture.g-vTextureBG.g)<u_Threshold && abs(vTexture.b-vTextureBG.b)<u_Threshold){
//				gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
//			}else{
//				gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
//			}
//		}else{
//			gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
//		}
//    }else{
//        if(abs(abs(hsv.b-hsvBG.b)-hsvDis)<0.5){
//            gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
//        }else{
//            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
//        }
//    }
    if(abs(vTexture.r-vTextureBG.r)<u_Threshold && abs(vTexture.g-vTextureBG.g)<u_Threshold && abs(vTexture.b-vTextureBG.b)<u_Threshold){
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
    }else{
        //if(abs(abs(hsv.r-hsvBG.r)-disHSV)<1.0){
        //    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
        //}else{
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
       // }
    }
}