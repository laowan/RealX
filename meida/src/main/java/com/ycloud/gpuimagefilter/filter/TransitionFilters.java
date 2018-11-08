package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.TransitionType;

/**
 * Created by DZHJ on 2017/7/22.
 */

public class TransitionFilters {

    public final String PassThroughVertString =
            "attribute vec4 aPosition;\n" +
                    "attribute vec2 aSamplerCoordinate;\n" +
                    "varying vec2 vSamplerCoordinate;\n" +
                    "uniform mat4 uModelViewProjMatrix;\n" +

                    "void main() { \n" +
                    "gl_Position = uModelViewProjMatrix * aPosition; \n" +
                    "vSamplerCoordinate = aSamplerCoordinate; \n" +
                    "}";

    // Fade
    public final String FadeFragString =

            "precision highp float;\n" +
                    "varying vec2 vSamplerCoordinate;\n" +
                    "uniform float uTweenFactor; \n" +
                    "uniform sampler2D uSamplerFrom;\n" +
                    "uniform sampler2D uSamplerTo;\n" +

                    "void main() {\n" +
                    "gl_FragColor = mix(texture2D(uSamplerFrom, vSamplerCoordinate), texture2D(uSamplerTo, vSamplerCoordinate), uTweenFactor); \n" +
                    "}";

    // Fold
    public final String FoldFragString =
            "precision highp float;\n" +
                    "varying vec2 vSamplerCoordinate;\n" +
                    "uniform float uTweenFactor;\n" +
                    "uniform sampler2D uSamplerFrom;\n" +
                    "uniform sampler2D uSamplerTo;\n" +

                    "void main() {\n" +
                    "vec4 fromTextureColor = texture2D(uSamplerFrom, (vSamplerCoordinate - vec2(uTweenFactor, 0.0)) / vec2(1.0 - uTweenFactor, 1.0));\n" +
                    "vec4 toTextureColor = texture2D(uSamplerTo, vSamplerCoordinate / vec2(uTweenFactor, 1.0));\n" +
                    "gl_FragColor = mix(fromTextureColor, toTextureColor, step(vSamplerCoordinate.x, uTweenFactor));\n" +
                    "}";


    // Wave Graffiti
    public final String WaveGraffitiFragString =
            "precision highp float;\n" +
                    "varying vec2 vSamplerCoordinate; \n" +
                    "uniform float uTweenFactor;\n" +
                    "uniform sampler2D uSamplerFrom;\n" +
                    "uniform sampler2D uSamplerTo; \n" +
                    "float compute(vec2 p, float progress, vec2 center) { \n" +
                    "float amplitude = 1.0; \n" +
                    "float waves = 30.0; \n" +
                    "float PI = 3.1415926; \n" +
                    "vec2 o = p * sin(progress * amplitude) - center; \n" +
                    "vec2 h = vec2(1.0, 0.0); \n" +
                    "float theta = acos(dot(o, h)) * waves; \n" +
                    "return (exp(cos(theta)) - 2.0 * cos(4.0 * theta) + pow(sin((2.0 * theta - PI) / 24.0), 5.0)) / 10.0; \n" +
                    "} \n" +

                    " void main() { \n" +
                    "float colorSeparation = 0.5; \n" +
                    "float inv = 1.0 - uTweenFactor;\n" +
                    "vec2 p = vSamplerCoordinate.xy / vec2(1.0).xy;\n" +
                    "vec2 dir = p - vec2(0.5);\n" +
                    "float dist = length(dir);\n" +
                    "float disp = compute(p, uTweenFactor, vec2(0.5, 0.5)) ;\n" +
                    "vec4 toTextureColor = texture2D(uSamplerTo, p + inv * disp);\n" +
                    "vec4 fromTextureColor = vec4(texture2D(uSamplerFrom, p + uTweenFactor * disp * (1.0 - colorSeparation)).r," +
                    "texture2D(uSamplerFrom, p + uTweenFactor * disp).g," +
                    "texture2D(uSamplerFrom, p + uTweenFactor * disp * (1.0 + colorSeparation)).b," +
                    "1.0);\n" +
                    "gl_FragColor = toTextureColor * uTweenFactor + fromTextureColor * inv;\n" +
                    "}\n";


    // crosswarp
    public final String CrossWarpFragString =
            "precision highp float;" +
                    "varying vec2 vSamplerCoordinate;" +
                    "uniform float uTweenFactor;" +
                    "uniform sampler2D uSamplerFrom;" +
                    "uniform sampler2D uSamplerTo;" +

                    "void main() {" +
                    "float process = smoothstep(0.0, 1.0, (uTweenFactor * 2.0 + vSamplerCoordinate.x - 1.0));" +
                    "vec4 toTextureColor = texture2D(uSamplerTo, (vSamplerCoordinate - 0.5) * process + 0.5);" +
                    "vec4 fromTextureColor = texture2D(uSamplerFrom, (vSamplerCoordinate - 0.5) * (1.0 - process) + 0.5);" +
                    "gl_FragColor = mix(fromTextureColor, toTextureColor, process);" +
                    "}";


    // radial
    public final String RadialFragString =
            "precision highp float;" +
                    "varying vec2 vSamplerCoordinate;" +
                    "uniform float uTweenFactor;" +
                    "uniform sampler2D uSamplerFrom;" +
                    "uniform sampler2D uSamplerTo;" +

                    "void main() {" +
                    "float smoothness = 1.0;" +
                    "float PI = 3.1415926;" +
                    "vec2 rp = vSamplerCoordinate * 2.0 - 1.0;" +
                    "vec4 toTextureColor = texture2D(uSamplerTo, vSamplerCoordinate);" +
                    "vec4 fromTextureColor = texture2D(uSamplerFrom, vSamplerCoordinate);" +
                    "gl_FragColor = mix(toTextureColor, fromTextureColor, smoothstep(0., smoothness, atan(rp.y, rp.x) - (uTweenFactor - 0.5) * PI * 2.5));" +
                    "}";

    // pin wheel
    public final String PinWheelFragString =
            "precision highp float;" +
                    "varying vec2 vSamplerCoordinate;" +
                    "uniform float uTweenFactor;" +
                    "uniform sampler2D uSamplerFrom;" +
                    "uniform sampler2D uSamplerTo;" +

                    "void main() {" +
                    "float speed = 2.0;" +
                    "vec2 p = vSamplerCoordinate.xy / vec2(1.0).xy;" +
                    "vec2 rp = vSamplerCoordinate * 2.0 - 1.0;" +
                    "float circPos = atan(p.y - 0.5, p.x - 0.5) + uTweenFactor * speed;" +
                    "float modPos = mod(circPos, 3.1415926 / 4.0);" +
                    "float signed = sign(uTweenFactor - modPos);" +

                    "vec4 toTextureColor = texture2D(uSamplerTo, p);" +
                    "vec4 fromTextureColor = texture2D(uSamplerFrom, p);" +
                    "gl_FragColor = mix(toTextureColor, fromTextureColor, step(signed, 0.5));" +
                    "}";

    String getTransitionVertString(TransitionType transitionType) {
        switch (transitionType) {
            case Fade:
            case Fold:
            case WaveGraffiti:
            case Crosswarp:
            case Radial:
            case PinWheel:
            default:
                return PassThroughVertString;
        }
    }


    String getTransitionFragString(TransitionType transitionType) {
        switch (transitionType) {
            case Fade:
                return FadeFragString;
            case Fold:
                return FoldFragString;
            case WaveGraffiti:
                return WaveGraffitiFragString;
            case Crosswarp:
                return CrossWarpFragString;
            case Radial:
                return RadialFragString;
            case PinWheel:
                return PinWheelFragString;
            default:
                return FadeFragString;
        }
    }
}
