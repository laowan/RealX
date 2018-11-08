package com.ycloud.gles;

/**
 * Created by kele on 20/6/2018.
 */

public class VertexShader {
    public static final String VERTEX_SHADER =
            "precision mediump float;    \n" +
                    "precision mediump int;      \n" +
                    "attribute vec2 a_position;  \n" +
                    "attribute vec2 a_texCoord;  \n" +
                    "varying vec2 v_texCoord;    \n" +
                    "uniform mat4 u_modelView;   \n" +
                    "uniform mat4 u_projection;  \n" +
                    "void main()                 \n" +
                    "{                           \n" +
                    "   vec4 value = vec4(a_position, 0.0, 1.0); \n" +
                    "   gl_Position = u_modelView * value;       \n" +
                    "   v_texCoord = a_texCoord; \n" +
                    "}                           \n";
}
