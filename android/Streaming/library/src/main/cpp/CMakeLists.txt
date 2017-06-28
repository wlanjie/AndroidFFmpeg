cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES=1")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES")

#add x264 header
#set(X264_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/x264-build/${ANDROID_ABI}/output/include)
#set(X264_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../extra/x264)
#include_directories(${X264_HEADER})

set(openh264_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openh264-build/${ANDROID_ABI}/output/include)
include_directories(${openh264_HEADER})

#link openh264 library
set(OPENH264_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openh264-build)
add_library(openh264 SHARED IMPORTED)
set_target_properties(openh264 PROPERTIES IMPORTED_LOCATION ${OPENH264_LIB_DIR}/${ANDROID_ABI}/output/lib/libopenh264.so)

#add libyuv header
include_directories(${PROJECT_SOURCE_DIR}/libyuv/include)

#add rtmp header
include_directories(${PROJECT_SOURCE_DIR}/rtmp/core)
include_directories(${PROJECT_SOURCE_DIR}/rtmp/kernel)
include_directories(${PROJECT_SOURCE_DIR}/rtmp/libs)
include_directories(${PROJECT_SOURCE_DIR}/rtmp/protocol)

#add fdk-aac header
include_directories(${PROJECT_SOURCE_DIR}/fdk-aac)

#add libyuv library
add_subdirectory(libyuv)

#add librtmp library
add_subdirectory(rtmp)

#add fdk-aac library
add_subdirectory(fdk-aac)

file(GLOB WLANJIE_SOURCE stream_jni.cpp muxer.cpp YuvConvert.cpp YuvFrame.cpp audioencode.cpp YuvConvert.cpp h264encode.cpp)
add_library(wlanjie SHARED ${WLANJIE_SOURCE})

#link libyuv library
set(YUV_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/libyuv)
add_library(YUV_LIB STATIC IMPORTED)
set_target_properties(YUV_LIB PROPERTIES IMPORTED_LOCATION ${YUV_LIB_DIR}/libyuv.a)

#link librtmp library
#set(LIBRTMP_DIR ${CMAKE_CURRENT_BINARY_DIR}/librtmp)
#add_library(LIBRTMP_LIB STATIC IMPORTED)
#set_target_properties(LIBRTMP_LIB PROPERTIES IMPORTED_LOCATION ${LIBRTMP_DIR}/librtmp.a)

#link libx264 library
#set(LIBX264_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/x264-build)
#add_library(X264 STATIC IMPORTED)
#set_target_properties(X264 PROPERTIES IMPORTED_LOCATION ${LIBX264_LIB_DIR}/${ANDROID_ABI}/output/lib/libx264.a)

#link fdk-aac library
set(AAC_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/fdk-aac)
add_library(AAC STATIC IMPORTED)
set_target_properties(AAC PROPERTIES IMPORTED_LOCATION ${AAC_LIB_DIR}/libaac.a)

#link rtmp library
set(RTMP_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/rtmp)
add_library(RTMP STATIC IMPORTED)
set_target_properties(RTMP PROPERTIES IMPORTED_LOCATION ${RTMP_LIB_DIR}/librtmp.a)

target_link_libraries(wlanjie YUV_LIB openh264 AAC RTMP android log dl m z GLESv1_CM GLESv2)