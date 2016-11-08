cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -DANDROID_NDK -DDISABLE_IMPORTGL -DGL_GLEXT_PROTOTYPES")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++98 -Wall -UNDEBUG -DGL_GLEXT_PROTOTYPES")

include_directories(.)
include_directories(libAACdec/include)
include_directories(libAACenc/include)
include_directories(libPCMutils/include)
include_directories(libFDK/include)
include_directories(libSYS/include)
include_directories(libMpegTPDec/include)
include_directories(libMpegTPEnc/include)
include_directories(libSBRdec/include)
include_directories(libSBRenc/include)

file(GLOB AAC_SOURCE libAACdec/src/*.cpp
                libAACenc/src/*.cpp
                libFDK/src/*.cpp
                libMpegTPDec/src/*.cpp
                libMpegTPEnc/src/*.cpp
                libPCMutils/src/*.cpp
                libSBRdec/src/*.cpp
                libSBRenc/src/*.cpp
                libSYS/src/*.cpp)
add_library(aac STATIC ${AAC_SOURCE})
target_link_libraries(aac)