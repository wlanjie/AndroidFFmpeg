cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-\#warnings -std=c99 -Wall -DANDROID_NDK")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++98 -Wall -UNDEBUG -DGL_GLEXT_PROTOTYPES")

include_directories(core)
include_directories(kernel)
include_directories(libs)
include_directories(protocol)

file(GLOB RTMP_SOURCE core/*.cpp kernel/*.cpp libs/*.cpp protocol/*.cpp)

add_library(rtmp STATIC ${RTMP_SOURCE})
target_link_libraries(rtmp)