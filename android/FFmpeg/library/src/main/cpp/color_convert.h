//
// Created by wlanjie on 16/9/11.
//

#ifndef FFMPEG_COLOR_CONVERT_H
#define FFMPEG_COLOR_CONVERT_H

#define COLOR_FORMAT_NV21 17

#define FLAG_DIRECTION_FLIP_HORIZONTAL	0x01
#define FLAG_DIRECTION_FLIP_VERTICAL	0x02
#define FLAG_DIRECTION_ROATATION_0 		0x10
#define FLAG_DIRECTION_ROATATION_90		0x20
#define FLAG_DIRECTION_ROATATION_180	0x40
#define FLAG_DIRECTION_ROATATION_270	0x80

void NV21ToYUV420SP(const unsigned char *src,const unsigned char *dst,int ySize);
void YUV420SPToYUV420P(const unsigned char *src,const unsigned char *dst,int ySize);
void NV21ToYUV420P(const unsigned char *src,const unsigned char *dst,int ySize);
void NV21ToARGB(const unsigned char *src,const unsigned int *dst,int width,int height);
void NV21Transform(const unsigned char *src,const unsigned char *dst,int dstWidth,int dstHeight,int directionFlag);
void NV21ToYUV(const unsigned char *src,const unsigned char *dstY,const unsigned char *dstU,const unsigned char *dstV,int width,int height);
void FIXGLPIXEL(const unsigned int *src,unsigned int *dst,int width,int height);

#endif //FFMPEG_COLOR_CONVERT_H
