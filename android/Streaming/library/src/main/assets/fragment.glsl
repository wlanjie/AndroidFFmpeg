#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES camTexture;

varying vec2 v_CamTexCoordinate;
varying vec2 v_TexCoordinate;

void main ()
{
    vec4 cameraColor = texture2D(camTexture, v_CamTexCoordinate);
    gl_FragColor = cameraColor;
}