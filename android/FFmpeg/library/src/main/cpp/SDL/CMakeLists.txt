cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DGL_GLEXT_PROTOTYPES")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG")

file(GLOB SDL_SOURCE src/*.c
                src/audio/*.c
                src/audio/android/*.c
                src/audio/dummy/*.c
                src/atomic/*.c
                src/core/android/*.c
                src/cpuinfo/*.c
                src/dynapi/*.c
                src/events/*.c
                src/file/*.c
                src/haptic/*.c
                src/haptic/dummy/*.c
                src/joystick/*.c
                src/joystick/android/*.c
                src/loadso/dlopen/*.c
                src/power/*.c
                src/power/android/*.c
                src/filesystem/android/*.c
                src/render/*.c
                src/render/*/*.c
                src/stdlib/*.c
                src/thread/*.c
                src/thread/pthread/*.c
                src/timer/*.c
                src/timer/unix/*.c
                src/video/*.c
                src/video/android/*.c)
add_library(SDL2 SHARED ${SDL_SOURCE})

target_link_libraries(SDL2 dl m GLESv1_CM GLESv2 log android)