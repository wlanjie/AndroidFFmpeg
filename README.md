##### FFmpeg Library for Android

##### Building

    export ANDROID_NDK=you_ndk_path

因为arm64和x86,x86-64使用了yasm,所以需要安装yasm,由于我使用的是mac,所以使用brew安装,```brew install yasm```,brew安装文档[brew](http://brew.sh/index_zh-cn.html)

然后在终端运行

    ./build-android.sh

会去下载ffmpeg和x264的源码,编译完成之后会在```tools```目录下的```ffmpeg-build```下生成对应的libffmpeg.so,编译完成之后把项目导入Android Studio就可以了.

##### 使用

![压缩](https://github.com/wlanjie/AndroidFFmpeg/blob/master/image/compress.png)

### 压缩

```java
FFmpeg ffmpeg = FFmpeg.getInstance();
ffmpeg.setInputDataSource("you input path");
ffmpeg.setOutputDataSource("you output path");
//视频的宽
int videoWidth = ffmpeg.getVideoWidth();
//视频的高
int videoHeight = ffmpeg.getVideoHeight();
//视频的角度,90,180,270,360
doule roation = ffmpeg.getRotation();
int result = ffmpeg.setCompress(需要缩放的宽,需要缩放的高);
ffmpeg.release();
//result >= 0 success
if (result < 0) {
    //error
}
```

### 裁剪

下面是裁剪的效果图


<figure>
    <img width="150" height="300" src="https://github.com/wlanjie/AndroidFFmpeg/blob/master/image/crop_before.png">
    <img width="150" height="300" src="https://github.com/wlanjie/AndroidFFmpeg/blob/master/image/crop_after.png">
    <img width="150" height="300" src="https://github.com/wlanjie/AndroidFFmpeg/blob/master/image/crop_description.png">
</figure>

```java
FFmpeg ffmpeg = FFmpeg.getInstance();
ffmpeg.setInputDataSource("you input path");
ffmpeg.setOutputDataSource("you outpu path");
int result = ffmpeg.crop(x, y, width, height);
ffmpeg.release();
// result >= 0 success
if (result < 0) {
    //error
}
```

### About me:

wlanjie,任职于teambition Android工程师
联系方式:qq:153920981

