cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=C99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG")
set(SOURCE_FILES
  ${SDL2_SOURCE_DIR}/src/*.c
  ${SDL2_SOURCE_DIR}/src/atomic/*.c
  ${SDL2_SOURCE_DIR}/src/audio/*.c
  ${SDL2_SOURCE_DIR}/src/cpuinfo/*.c
  ${SDL2_SOURCE_DIR}/src/dynapi/*.c
  ${SDL2_SOURCE_DIR}/src/events/*.c
  ${SDL2_SOURCE_DIR}/src/file/*.c
  ${SDL2_SOURCE_DIR}/src/libm/*.c
  ${SDL2_SOURCE_DIR}/src/render/*.c
  ${SDL2_SOURCE_DIR}/src/render/*/*.c
  ${SDL2_SOURCE_DIR}/src/stdlib/*.c
  ${SDL2_SOURCE_DIR}/src/thread/*.c
  ${SDL2_SOURCE_DIR}/src/timer/*.c
  ${SDL2_SOURCE_DIR}/src/video/*.c
  ${SDL2_SOURCE_DIR}/src/core/android/*.c
  ${SDL2_SOURCE_DIR}/src/audio/android/*.c
  ${SDL2_SOURCE_DIR}/src/filesystem/android/*.c
  ${SDL2_SOURCE_DIR}/src/joystick/android/*.c
  ${SDL2_SOURCE_DIR}/src/power/android/*.c
  ${SDL2_SOURCE_DIR}/src/video/android/*.c)

add_library(SDL2 SHARED ${SOURCE_FILES})
#SET_TARGET_PROPERTIES(SDL2 PROPERTIES LINKER_LANGUAGE C)
target_link_libraries(SDL2 android log Wl dl GLESv1_CM GLESv2)