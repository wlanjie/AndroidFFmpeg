# 推流 所在项目 Streaming

![推流](art/streaming.gif)

#### 左边是推流端,右边是播放端, 由于gif图片过大,如果加载不出来,请查看art/streaming.gif

### 开发工具
Android Studio 3.0

Android NDK r12

使用的开源库
------

- [openh264](https://github.com/cisco/openh264)
- [fdk-aac](https://github.com/mstorsjo/fdk-aac)
- [srs-librtmp](https://github.com/ossrs/srs)
- [libyuv](https://chromium.googlesource.com/libyuv/libyuv/)

支持如下功能:
-------

- [x] H.264/AAC 硬编 Api 18支持.
- [x] H.264/AAC 软编 Api 16.
- [x] 更多可选项配置(正在开发中).
- [x] 水印(正在开发中).

### 视频编码配置
- 硬编 使用MediaCodec编码.
- 软编 使用FBO读取纹理数据,由于使用FBO读取的数据是上下颠倒的,故而使用libyuv将图像旋转了180度,openh264编码.

### 音频编码配置
- 硬编 使用MediaCodec编码.
- 软编 使用fdk-aac编码为aac数据.

使用方式:

onCreate中设置初始化

```java

CameraSetting cameraSetting = new CameraSetting();
AudioSetting audioSetting = new AudioSetting();
StreamingSetting streamingSetting = new StreamingSetting();
streamingSetting.setRtmpUrl("rtmp://www.ossrs.net:1935/live/demo")
    .setEncoderType(EncoderType.SOFT);

GLSurfaceView glSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
mMediaStreamingManager = new MediaStreamingManager(glSurfaceView);
mMediaStreamingManager.prepare(cameraSetting, streamingSetting, audioSetting);
```

### 打开摄像头在```onResume```中调用
```java
mMediaStreamingManager.resume();
```

### 释放摄像头```onPause```中调用
```java
mMediaStreamingManager.pause();
```

### 开始推流
```java
mMediaStreamingManager.startStreaming();
```

### 停止推流
```java
mMediaStreamingManager.stopStreaming();
```

### 自定义滤镜
实现setSurfaceTextureCallback接口
```
mMediaStreamingManager.setSurfaceTextureCallback(this);
```
```
public int onDrawFrame(int textureId, int textureWidth, int textureHeight, float[] transformMatrix)
```

在onDrawFrame函数中实现滤镜处理,这里的textureId参数为```GLES11Ext.GL_TEXTURE_EXTERNAL_OES```类型,```textureWidth```为纹理的宽度,```textureHeight```为纹理的高度,```transformMatrix```为纹理的```textureTransform```数组,返回值如果<=0或者是textureId代表不处理滤镜,否则需要返回一个```GLES20.GL_TEXTURE_2D```类型的纹理id,示例中使用了[MagicCamera](https://github.com/wuhaoyu1990/MagicCamera)作为滤镜处理库.

### 关于我

wlanjie，
联系方式:qq:153920981 微信:w153920981
