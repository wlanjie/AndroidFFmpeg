cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG")

#add ffmpeg header
set(FFMPEG_HEADER ${CMAKE_CURRENT_LIST_DIR}/../../../../../../tools/ffmpeg-build/${ANDROID_ABI}/output/include)
include_directories(${FFMPEG_HEADER})
#add SDL2 header
include_directories(${PROJECT_SOURCE_DIR}/SDL/include)
#add libyuv header
include_directories(${PROJECT_SOURCE_DIR}/libyuv/include)

#add SDL2 library
add_subdirectory(SDL)

#add libyuv library
add_subdirectory(libyuv)

file(GLOB WLANJIE_SOURCE *.c)
add_library(wlanjie SHARED ${WLANJIE_SOURCE})

message(STATUS "cmake build dir : ${CMAKE_CURRENT_LIST_DIR}")
message(STATUS "ffmpeg lib: ${FFMPEG_LIB_DIR}")
message(STATUS "CMAKE_CURRENT_BINARY_DIR: ${CMAKE_CURRENT_BINARY_DIR}")

#link SDL2 library
set(SDL2_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../.externalNativeBuild/cmake/debug/obj)
message(STATUS "SDL2_LIB_DIR: ${SDL2_LIB_DIR}")
add_library(SDL2_LIB SHARED IMPORTED)
set_target_properties(SDL2_LIB PROPERTIES IMPORTED_LOCATION ${SDL2_LIB_DIR}/${ANDROID_ABI}/libSDL2.so)

#link libyuv library
set(YUV_LIB_DIR ${CMAKE_CURRENT_BINARY_DIR}/libyuv)
add_library(YUV_LIB STATIC IMPORTED)
set_target_properties(YUV_LIB PROPERTIES IMPORTED_LOCATION ${YUV_LIB_DIR}/libyuv.a)

#link ffmpeg library
set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_LIST_DIR}/../jniLibs)
add_library(ffmpeg SHARED IMPORTED)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/${ANDROID_ABI}/libffmpeg.so)

target_link_libraries(wlanjie SDL2_LIB YUV_LIB android log dl m GLESv1_CM ffmpeg)