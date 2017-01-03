cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES=1")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES")

#add ffmpeg header
#set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../extra/ffmpeg)
set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/ffmpeg-build/${ANDROID_ABI}/output/include)
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

#add SDL2 library
add_subdirectory(SDL)

#add libyuv library
add_subdirectory(libyuv)

file(GLOB WLANJIE_SOURCE *.c)
add_library(wlanjie SHARED ${WLANJIE_SOURCE})

#link SDL2 library
set(SDL2_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/SDL)
message(STATUS "SDL2_LIB_DIR: ${SDL2_LIB_DIR}")
add_library(SDL2_LIB STATIC IMPORTED)
set_target_properties(SDL2_LIB PROPERTIES IMPORTED_LOCATION ${SDL2_LIB_DIR}/libSDL2.a)

#link libyuv library
set(YUV_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/libyuv)
add_library(YUV_LIB STATIC IMPORTED)
set_target_properties(YUV_LIB PROPERTIES IMPORTED_LOCATION ${YUV_LIB_DIR}/libyuv.a)

#link ffmpeg library
set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../jniLibs)
add_library(ffmpeg SHARED IMPORTED)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/${ANDROID_ABI}/libffmpeg.so)

#link libx264 library
set(LIBX264_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/x264-build)
add_library(X264 STATIC IMPORTED)
set_target_properties(X264 PROPERTIES IMPORTED_LOCATION ${LIBX264_LIB_DIR}/${ANDROID_ABI}/output/lib/libx264.a)

target_link_libraries(wlanjie SDL2_LIB YUV_LIB X264 android log dl m z GLESv1_CM GLESv2 ffmpeg)