cmake_minimum_required(VERSION 3.4.1)

set(openh264_header ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openh264-build/${ANDROID_ABI}/output/include)
include_directories(${openh264_header})

#add libyuv header
include_directories(${PROJECT_SOURCE_DIR}/libyuv/include)

#add rtmp header
#include_directories(${PROJECT_SOURCE_DIR}/rtmp/core)
#include_directories(${PROJECT_SOURCE_DIR}/rtmp/kernel)
#include_directories(${PROJECT_SOURCE_DIR}/rtmp/libs)
#include_directories(${PROJECT_SOURCE_DIR}/rtmp/protocol)

#add fdk-aac header
include_directories(${PROJECT_SOURCE_DIR}/fdk-aac)

#add libyuv library
add_subdirectory(libyuv)

#add librtmp library
#add_subdirectory(rtmp)

#add fdk-aac library
add_subdirectory(fdk-aac)

file(GLOB source stream_jni.cpp audioencode.cpp h264encode.cpp srs_librtmp.cpp)
add_library(wlanjie SHARED ${source})

#link libyuv library
set(YUV_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/libyuv)
add_library(YUV_LIB STATIC IMPORTED)
set_target_properties(YUV_LIB PROPERTIES IMPORTED_LOCATION ${YUV_LIB_DIR}/libyuv.a)

#link librtmp library
#set(LIBRTMP_DIR ${CMAKE_CURRENT_BINARY_DIR}/librtmp)
#add_library(LIBRTMP_LIB STATIC IMPORTED)
#set_target_properties(LIBRTMP_LIB PROPERTIES IMPORTED_LOCATION ${LIBRTMP_DIR}/librtmp.a)

#link fdk-aac library
set(AAC_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/fdk-aac)
add_library(AAC STATIC IMPORTED)
set_target_properties(AAC PROPERTIES IMPORTED_LOCATION ${AAC_LIB_DIR}/libaac.a)

#link rtmp library
#set(RTMP_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/rtmp)
#add_library(RTMP STATIC IMPORTED)
#set_target_properties(RTMP PROPERTIES IMPORTED_LOCATION ${RTMP_LIB_DIR}/librtmp.a)

#link openh264 library
set(openh264_lib ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openh264-build)
add_library(openh264 SHARED IMPORTED)
set_target_properties(openh264 PROPERTIES IMPORTED_LOCATION ${openh264_lib}/${ANDROID_ABI}/output/lib/libopenh264.so)

target_link_libraries(wlanjie YUV_LIB openh264 AAC android log dl m)