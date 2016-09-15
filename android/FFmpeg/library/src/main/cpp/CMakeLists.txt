cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES=1")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG -DNO_CRYPTO -DGL_GLEXT_PROTOTYPES")

#add ffmpeg header
set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../extra/ffmpeg)
#set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/ffmpeg-build/${ANDROID_ABI}/output/include)
include_directories(${FFMPEG_HEADER})
#add SDL2 header
include_directories(${PROJECT_SOURCE_DIR}/SDL/include)
include_directories(${PROJECT_SOURCE_DIR}/SDL/src/core/android)
#add libyuv header
include_directories(${PROJECT_SOURCE_DIR}/libyuv/include)
#add librtmp header
include_directories(${PROJECT_SOURCE_DIR}/rtmpdump)
include_directories(${PROJECT_SOURCE_DIR}/librtmp)
#add openssl header
include_directories(${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openssl-build/${ANDROID_ABI}/output/include)

#add SDL2 library
add_subdirectory(SDL)

#add libyuv library
add_subdirectory(libyuv)

#add librtmp library
#add_subdirectory(librtmp)

file(GLOB WLANJIE_SOURCE *.c rtmpdump/rtmpdump.c rtmpdump/thread.c rtmpdump/librtmp/*.c)
add_library(wlanjie SHARED ${WLANJIE_SOURCE})

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

#link openssl library
set(OPENSSL_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/openssl-build)
add_library(OPENSSL STATIC IMPORTED)
set_target_properties(OPENSSL PROPERTIES IMPORTED_LOCATION ${OPENSSL_LIB_DIR}/${ANDROID_ABI}/output/lib/libssl.a)

target_link_libraries(wlanjie SDL2_LIB YUV_LIB OPENSSL android log dl m z GLESv1_CM GLESv2 ffmpeg)