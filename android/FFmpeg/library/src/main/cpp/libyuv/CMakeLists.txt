cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DGL_GLEXT_PROTOTYPES")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -UNDEBUG -DGL_GLEXT_PROTOTYPES")

include_directories(include)

file(GLOB YUV_SOURCE source/*.cc)
add_library(yuv STATIC ${YUV_SOURCE})
target_link_libraries(yuv)