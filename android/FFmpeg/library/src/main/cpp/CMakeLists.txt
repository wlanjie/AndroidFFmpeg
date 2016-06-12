cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG")

project(MyProject)
set(FFMPEG_HEADER /Users/wlanjie/Documents/FFmpeg/tools/ffmpeg-build/x86/output/include)
include_directories(${FFMPEG_HEADER})
set(wlanjie_SRCS ffmpeg.c
                    filter.c
                    openfile.c
                    utils.c
                    wlanjie_jni.c)
add_library(wlanjie SHARED ${wlanjie_SRCS})

link_directories(${/Users/wlanjie/Documents/FFmpeg/android/FFmpeg/library/src/main/jniLibs/x86})

#add_subdirectory(SDL2)
target_link_libraries(wlanjie android log dl m GLESv1_CM /Users/wlanjie/Documents/FFmpeg/android/FFmpeg/library/src/main/jniLibs/x86/libffmpeg.so)