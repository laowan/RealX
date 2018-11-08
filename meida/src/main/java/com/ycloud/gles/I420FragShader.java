package com.ycloud.gles;

/**
 * Created by kele on 20/6/2018.
 */

public class I420FragShader {
    public static final String I420_FRAGMENT_SHADER =
            "precision mediump float;  \n" +
                    "precision mediump int;    \n" +
                    "varying vec2 v_texCoord;  \n" +
                    "uniform sampler2D u_texY; \n" +
                    "uniform sampler2D u_texU; \n" +
                    "uniform sampler2D u_texV; \n" +
                    "const mat3 op = mat3(1.164, 1.164, 1.164, 0.0, -0.391, 2.018, 1.596, -0.813, 0.0); \n" +
                    "void main(void)   \n" +
                    "{                 \n" +
                    "   vec3 rgb, yuv; \n" +
                    "   yuv.x = texture2D(u_texY, v_texCoord).r - 0.0625; \n" +
                    "   yuv.y = texture2D(u_texU, v_texCoord).r - 0.5;    \n" +
                    "   yuv.z = texture2D(u_texV, v_texCoord).r - 0.5;    \n" +
                    "   rgb = op * yuv;                 \n" +
                    "   rgb.r = clamp(rgb.r, 0.0, 1.0); \n" +
                    "   rgb.g = clamp(rgb.g, 0.0, 1.0); \n" +
                    "   rgb.b = clamp(rgb.b, 0.0, 1.0); \n" +
                    "   gl_FragColor = vec4(rgb, 1.0);  \n" +
                    "}                                  \n";
}
