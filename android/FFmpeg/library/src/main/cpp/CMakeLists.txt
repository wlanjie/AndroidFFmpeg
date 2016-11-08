cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES=1")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES")

#add ffmpeg header
set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../extra/ffmpeg)
#set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/ffmpeg-build/${ANDROID_ABI}/output/include)
include_directories(${FFMPEG_HEADER})
#add x264 header
set(X264_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/x264-build/${ANDROID_ABI}/output/include)
#set(X264_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../extra/x264)
include_directories(${X264_HEADER})
#add SDL2 header
include_directories(${PROJECT_SOURCE_DIR}/SDL/include)
include_directories(${PROJECT_SOURCE_DIR}/SDL/src/core/android)
#add libyuv header
include_directories(${PROJECT_SOURCE_DIR}/libyuv/include)
#add openssl header
include_directories(${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openssl-build/${ANDROID_ABI}/output/include)
#add rtmp header
#include_directories(${PROJECT_SOURCE_DIR}/rtmp)
#add fdk-aac header
include_directories(${PROJECT_SOURCE_DIR}/fdk-aac)

#add SDL2 library
add_subdirectory(SDL)

#add libyuv library
add_subdirectory(libyuv)

#add librtmp library
#add_subdirectory(rtmp)

#add fdk-aac library
add_subdirectory(fdk-aac)

file(GLOB WLANJIE_SOURCE *.c *.cpp *.cc srs_librtmp.cpp)
add_library(wlanjie SHARED ${WLANJIE_SOURCE})

message(STATUS "source dir: ${PROJECT_SOURCE_DIR}")
message(STATUS "cmake build dir : ${CMAKE_CURRENT_LIST_DIR}")
message(STATUS "ffmpeg lib: ${FFMPEG_LIB_DIR}")
message(STATUS "CMAKE_CURRENT_BINARY_DIR: ${CMAKE_CURRENT_BINARY_DIR}")

#link SDL2 library
set(SDL2_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/SDL)
message(STATUS "SDL2_LIB_DIR: ${SDL2_LIB_DIR}")
add_library(SDL2_LIB STATIC IMPORTED)
set_target_properties(SDL2_LIB PROPERTIES IMPORTED_LOCATION ${SDL2_LIB_DIR}/libSDL2.a)

#link libyuv library
set(YUV_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/libyuv)
add_library(YUV_LIB STATIC IMPORTED)
set_target_properties(YUV_LIB PROPERTIES IMPORTED_LOCATION ${YUV_LIB_DIR}/libyuv.a)

#link librtmp library
#set(LIBRTMP_DIR ${CMAKE_CURRENT_BINARY_DIR}/librtmp)
#add_library(LIBRTMP_LIB STATIC IMPORTED)
#set_target_properties(LIBRTMP_LIB PROPERTIES IMPORTED_LOCATION ${LIBRTMP_DIR}/librtmp.a)

#link ffmpeg library
set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../jniLibs)
add_library(ffmpeg SHARED IMPORTED)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/${ANDROID_ABI}/libffmpeg.so)

#link libx264 library
set(LIBX264_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/x264-build)
add_library(X264 STATIC IMPORTED)
set_target_properties(X264 PROPERTIES IMPORTED_LOCATION ${LIBX264_LIB_DIR}/${ANDROID_ABI}/output/lib/libx264.a)

#link fdk-aac library
set(AAC_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/fdk-aac)
add_library(AAC STATIC IMPORTED)
set_target_properties(AAC PROPERTIES IMPORTED_LOCATION ${AAC_LIB_DIR}/libaac.a)

#link rtmp library
#set(RTMP_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/rtmp)
#add_library(RTMP STATIC IMPORTED)
#set_target_properties(RTMP PROPERTIES IMPORTED_LOCATION ${RTMP_LIB_DIR}/librtmp.a)

target_link_libraries(wlanjie SDL2_LIB YUV_LIB X264 AAC android log dl m z GLESv1_CM GLESv2 ffmpeg)