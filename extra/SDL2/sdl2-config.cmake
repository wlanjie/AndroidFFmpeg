# sdl2 cmake project-config input for ./configure scripts

set(prefix "/Users/wlanjie/Documents/FFmpeg/tools/sdl2-build/x86/output") 
set(exec_prefix "${prefix}")
set(libdir "${exec_prefix}/lib")
set(SDL2_PREFIX "/Users/wlanjie/Documents/FFmpeg/tools/sdl2-build/x86/output")
set(SDL2_EXEC_PREFIX "/Users/wlanjie/Documents/FFmpeg/tools/sdl2-build/x86/output")
set(SDL2_LIBDIR "${exec_prefix}/lib")
set(SDL2_INCLUDE_DIRS "${prefix}/include/SDL2")
set(SDL2_LIBRARIES "-L${SDL2_LIBDIR} -Wl,-rpath,${libdir} -lSDL2 ")
