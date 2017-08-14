cmake_minimum_required(VERSION 3.4.1)

file(GLOB source MagicJni.cpp beautify/MagicBeautify.cpp bitmap/BitmapOperation.cpp bitmap/Conversion.cpp)
add_library(MagicBeautify SHARED ${source})

target_link_libraries(MagicBeautify log jnigraphics)