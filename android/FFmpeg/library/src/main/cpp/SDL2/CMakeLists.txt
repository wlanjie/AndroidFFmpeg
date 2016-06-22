cmake_minimum_required(VERSION 2.8)
project(SDL2 C)
include(CheckFunctionExists)
include(CheckLibraryExists)
include(CheckIncludeFiles)
include(CheckIncludeFile)
include(CheckSymbolExists)
include(CheckCSourceRuns)
include(CheckCCompilerFlag)
include(CheckTypeSize)
include(CheckStructHasMember)
include(CMakeDependentOption)
include(FindPkgConfig)
#set(CMAKE_MODULE_PATH "${SDL2_SOURCE_DIR}/cmake")
include(${SDL2_SOURCE_DIR}/cmake/macros.cmake)
include(${SDL2_SOURCE_DIR}/cmake/sdlchecks.cmake)

# General settings
# Edit include/SDL_version.h and change the version, then:
#   SDL_MICRO_VERSION += 1;
#   SDL_INTERFACE_AGE += 1;
#   SDL_BINARY_AGE += 1;
# if any functions have been added, set SDL_INTERFACE_AGE to 0.
# if backwards compatibility has been broken,
# set SDL_BINARY_AGE and SDL_INTERFACE_AGE to 0.
set(SDL_MAJOR_VERSION 2)
set(SDL_MINOR_VERSION 0)
set(SDL_MICRO_VERSION 4)
set(SDL_INTERFACE_AGE 0)
set(SDL_BINARY_AGE 4)
set(SDL_VERSION "${SDL_MAJOR_VERSION}.${SDL_MINOR_VERSION}.${SDL_MICRO_VERSION}")

# Calculate a libtool-like version number
math(EXPR LT_CURRENT "${SDL_MICRO_VERSION} - ${SDL_INTERFACE_AGE}")
math(EXPR LT_AGE "${SDL_BINARY_AGE} - ${SDL_INTERFACE_AGE}")
math(EXPR LT_MAJOR "${LT_CURRENT}- ${LT_AGE}")
set(LT_REVISION "${SDL_INTERFACE_AGE}")
set(LT_RELEASE "${SDL_MAJOR_VERSION}.${SDL_MINOR_VERSION}")
set(LT_VERSION "${LT_MAJOR}.${LT_AGE}.${LT_REVISION}")

# Those are used for pkg-config and friends, so that the SDL2.pc, sdl2-config,
# etc. are created correctly.
set(SDL_LIBS "-lSDL2")
set(SDL_CFLAGS "")

# Emscripten toolchain has a nonempty default value for this, and the checks
# in this file need to change that, so remember the original value, and
# restore back to that afterwards. For check_function_exists() to work in
# Emscripten, this value must be at its default value.
set(ORIG_CMAKE_REQUIRED_FLAGS ${CMAKE_REQUIRED_FLAGS})

add_definitions(-DUSING_GENERATED_CONFIG_H)
# General includes
include_directories(${SDL2_BINARY_DIR}/include ${SDL2_SOURCE_DIR}/include)

# All these ENABLED_BY_DEFAULT vars will default to ON if not specified, so
#  you only need to have a platform override them if they are disabling.
set(OPT_DEF_ASM TRUE)

if (NOT DEFINED SDL_SHARED_ENABLED_BY_DEFAULT)
    set(SDL_SHARED_ENABLED_BY_DEFAULT ON)
endif()

set(SDL_SUBSYSTEMS
    Atomic Audio Video Render Events Joystick Haptic Power Threads Timers
    File Loadso CPUinfo Filesystem Dlopen)
foreach(_SUB ${SDL_SUBSYSTEMS})
  string(TOUPPER ${_SUB} _OPT)
  if (NOT DEFINED SDL_${_OPT}_ENABLED_BY_DEFAULT)
    set(SDL_${_OPT}_ENABLED_BY_DEFAULT ON)
  endif()
  option(SDL_${_OPT} "Enable the ${_SUB} subsystem" ${SDL_${_OPT}_ENABLED_BY_DEFAULT})
endforeach()

option_string(ASSERTIONS "Enable internal sanity checks (auto/disabled/release/enabled/paranoid)" "auto")
#set_option(DEPENDENCY_TRACKING "Use gcc -MMD -MT dependency tracking" ON)
set_option(LIBC                "Use the system C library" ${OPT_DEF_LIBC})
set_option(GCC_ATOMICS         "Use gcc builtin atomics" ${USE_GCC})
set_option(ASSEMBLY            "Enable assembly routines" ${OPT_DEF_ASM})
set_option(SSEMATH             "Allow GCC to use SSE floating point math" ${OPT_DEF_SSEMATH})
set_option(MMX                 "Use MMX assembly routines" ${OPT_DEF_ASM})
set_option(3DNOW               "Use 3Dnow! MMX assembly routines" ${OPT_DEF_ASM})
set_option(SSE                 "Use SSE assembly routines" ${OPT_DEF_ASM})
set_option(SSE2                "Use SSE2 assembly routines" ${OPT_DEF_SSEMATH})
set_option(ALTIVEC             "Use Altivec assembly routines" ${OPT_DEF_ASM})
set_option(DISKAUDIO           "Support the disk writer audio driver" ON)
set_option(DUMMYAUDIO          "Support the dummy audio driver" ON)
set_option(VIDEO_DIRECTFB      "Use DirectFB video driver" OFF)
dep_option(DIRECTFB_SHARED     "Dynamically load directfb support" ON "VIDEO_DIRECTFB" OFF)
set_option(FUSIONSOUND         "Use FusionSound audio driver" OFF)
dep_option(FUSIONSOUND_SHARED  "Dynamically load fusionsound audio support" ON "FUSIONSOUND" OFF)
set_option(VIDEO_DUMMY         "Use dummy video driver" ON)
set_option(VIDEO_OPENGL        "Include OpenGL support" ON)
set_option(VIDEO_OPENGLES      "Include OpenGL ES support" ON)
set_option(PTHREADS            "Use POSIX threads for multi-threading" ${SDL_PTHREADS_ENABLED_BY_DEFAULT})
dep_option(PTHREADS_SEM        "Use pthread semaphores" ON "PTHREADS" OFF)
set_option(SDL_DLOPEN          "Use dlopen for shared object loading" ${SDL_DLOPEN_ENABLED_BY_DEFAULT})
set_option(OSS                 "Support the OSS audio API" ${UNIX_SYS})
set_option(ALSA                "Support the ALSA audio API" ${UNIX_SYS})
dep_option(ALSA_SHARED         "Dynamically load ALSA audio support" ON "ALSA" OFF)
set_option(ESD                 "Support the Enlightened Sound Daemon" ${UNIX_SYS})
dep_option(ESD_SHARED          "Dynamically load ESD audio support" ON "ESD" OFF)
set_option(PULSEAUDIO          "Use PulseAudio" ${UNIX_SYS})
dep_option(PULSEAUDIO_SHARED   "Dynamically load PulseAudio support" ON "PULSEAUDIO" OFF)
set_option(ARTS                "Support the Analog Real Time Synthesizer" ${UNIX_SYS})
dep_option(ARTS_SHARED         "Dynamically load aRts audio support" ON "ARTS" OFF)
set_option(NAS                 "Support the NAS audio API" ${UNIX_SYS})
set_option(NAS_SHARED          "Dynamically load NAS audio API" ${UNIX_SYS})
set_option(SNDIO               "Support the sndio audio API" ${UNIX_SYS})
set_option(RPATH               "Use an rpath when linking SDL" ${UNIX_SYS})
set_option(CLOCK_GETTIME       "Use clock_gettime() instead of gettimeofday()" OFF)
set_option(INPUT_TSLIB         "Use the Touchscreen library for input" ${UNIX_SYS})
set_option(VIDEO_X11           "Use X11 video driver" ${UNIX_SYS})
set_option(VIDEO_WAYLAND       "Use Wayland video driver" ${UNIX_SYS})
dep_option(WAYLAND_SHARED      "Dynamically load Wayland support" ON "VIDEO_WAYLAND" OFF)
dep_option(VIDEO_WAYLAND_QT_TOUCH  "QtWayland server support for Wayland video driver" ON "VIDEO_WAYLAND" OFF)
set_option(VIDEO_MIR           "Use Mir video driver" ${UNIX_SYS})
dep_option(MIR_SHARED          "Dynamically load Mir support" ON "VIDEO_MIR" OFF)
set_option(VIDEO_RPI           "Use Raspberry Pi video driver" ${UNIX_SYS})
dep_option(X11_SHARED          "Dynamically load X11 support" ON "VIDEO_X11" OFF)
set(SDL_X11_OPTIONS Xcursor Xinerama XInput Xrandr Xscrnsaver XShape Xvm)
foreach(_SUB ${SDL_X11_OPTIONS})
  string(TOUPPER "VIDEO_X11_${_SUB}" _OPT)
  dep_option(${_OPT}           "Enable ${_SUB} support" ON "VIDEO_X11" OFF)
endforeach()
set_option(VIDEO_COCOA         "Use Cocoa video driver" ${APPLE})
set_option(DIRECTX             "Use DirectX for Windows audio/video" ${WINDOWS})
set_option(RENDER_D3D          "Enable the Direct3D render driver" ${WINDOWS})
set_option(VIDEO_VIVANTE       "Use Vivante EGL video driver" ${UNIX_SYS})

# TODO: We should (should we?) respect cmake's ${BUILD_SHARED_LIBS} flag here
# The options below are for compatibility to configure's default behaviour.
set(SDL_SHARED ${SDL_SHARED_ENABLED_BY_DEFAULT} CACHE BOOL "Build a shared version of the library")
set(SDL_STATIC ON CACHE BOOL "Build a static version of the library")

# General source files
file(GLOB SOURCE_FILES
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
  ${SDL2_SOURCE_DIR}/src/video/*.c)

  if(ANDROID)
    file(GLOB ANDROID_CORE_SOURCES ${SDL2_SOURCE_DIR}/src/core/android/*.c)
    set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_CORE_SOURCES})
    if(SDL_AUDIO)
      set(SDL_AUDIO_DRIVER_ANDROID 1)
      file(GLOB ANDROID_AUDIO_SOURCES ${SDL2_SOURCE_DIR}/src/audio/android/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_AUDIO_SOURCES})
      set(HAVE_SDL_AUDIO TRUE)
    endif()
    if(SDL_FILESYSTEM)
      set(SDL_FILESYSTEM_ANDROID 1)
      file(GLOB ANDROID_FILESYSTEM_SOURCES ${SDL2_SOURCE_DIR}/src/filesystem/android/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_FILESYSTEM_SOURCES})
      set(HAVE_SDL_FILESYSTEM TRUE)
    endif()
    if(SDL_JOYSTICK)
      set(SDL_JOYSTICK_ANDROID 1)
      file(GLOB ANDROID_JOYSTICK_SOURCES ${SDL2_SOURCE_DIR}/src/joystick/android/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_JOYSTICK_SOURCES})
      set(HAVE_SDL_JOYSTICK TRUE)
    endif()
    if(SDL_POWER)
      set(SDL_POWER_ANDROID 1)
      file(GLOB ANDROID_POWER_SOURCES ${SDL2_SOURCE_DIR}/src/power/android/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_POWER_SOURCES})
      set(HAVE_SDL_POWER TRUE)
    endif()
    if (SDL_TIMERS)
      set(SDL_TIMERS_ANDROID 1)
      set(SDL_TIMER_UNIX 1)
      file(GLOB ANDROID_TIMERS_SOURCE ${SDL2_SOURCE_DIR}/src/timer/unix/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_TIMERS_SOURCE})
      set(HAVE_SDL_TIMERS TRUE)
    endif()
    if(SDL_THREADS)
      set(SDL_THREADS_ANDROID 1)
      set(SDL_THREAD_PTHREAD 1)
      file(GLOB ANDROID_THREADS_SOURCE ${SDL2_SOURCE_DIR}/src/thread/pthread/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_THREADS_SOURCE})
      set(HAVE_SDL_THREADS TRUE)
    endif()
    if(SDL_VIDEO)
      set(SDL_VIDEO_DRIVER_ANDROID 1)
      file(GLOB ANDROID_VIDEO_SOURCES ${SDL2_SOURCE_DIR}/src/video/android/*.c)
      set(SOURCE_FILES ${SOURCE_FILES} ${ANDROID_VIDEO_SOURCES})
      set(HAVE_SDL_VIDEO TRUE)

      #enable gles
      if(VIDEO_OPENGLES)
        set(SDL_VIDEO_OPENGL_EGL 1)
        set(HAVE_VIDEO_OPENGLES TRUE)
        set(SDL_VIDEO_OPENGL_ES2 1)
        set(SDL_VIDEO_RENDER_OGL_ES2 1)
      endif()
    endif()
    list(APPEND EXTRA_LDFLAGS "-Wl,--undefined=Java_org_libsdl_app_SDLActivity_nativeInit")
  endif()

message(STATUS "SDL_TIMERS: ${SDL_TIMERS} HAVE_SDL_TIMERS: ${HAVE_SDL_TIMERS}")
foreach(_SUB ${SDL_SUBSYSTEMS})
  string(TOUPPER ${_SUB} _OPT)
  message_bool_option(${_SUB} SDL_${_OPT})
endforeach()

if(ASSERTIONS STREQUAL "auto")
  # Do nada - use optimization settings to determine the assertion level
elseif(ASSERTIONS STREQUAL "disabled")
  set(SDL_DEFAULT_ASSERT_LEVEL 0)
elseif(ASSERTIONS STREQUAL "release")
  set(SDL_DEFAULT_ASSERT_LEVEL 1)
elseif(ASSERTIONS STREQUAL "enabled")
  set(SDL_DEFAULT_ASSERT_LEVEL 2)
elseif(ASSERTIONS STREQUAL "paranoid")
  set(SDL_DEFAULT_ASSERT_LEVEL 3)
else()
  message_error("unknown assertion level")
endif()
set(HAVE_ASSERTIONS ${ASSERTIONS})

# TODO: Can't deactivate on FreeBSD? w/o LIBC, SDL_stdinc.h can't define
# anything.
if(LIBC)
  if(WINDOWS AND NOT MINGW)
    set(HAVE_LIBC TRUE)
    foreach(_HEADER stdio.h string.h ctype.h math.h)
      string(TOUPPER "HAVE_${_HEADER}" _UPPER)
      string(REPLACE "." "_" _HAVE_H ${_UPPER})
      set(${_HAVE_H} 1)
    endforeach()
    set(HAVE_SIGNAL_H 1)
    foreach(_FN
            malloc calloc realloc free qsort abs memset memcpy memmove memcmp
            strlen _strrev _strupr _strlwr strchr strrchr strstr itoa _ltoa
            _ultoa strtol strtoul strtoll strtod atoi atof strcmp strncmp
            _stricmp _strnicmp sscanf atan atan2 acos asin ceil copysign cos
            cosf fabs floor log pow scalbn sin sinf sqrt sqrtf tan tanf)
      string(TOUPPER ${_FN} _UPPER)
      set(HAVE_${_UPPER} 1)
    endforeach()
    if(NOT CYGWIN AND NOT MINGW)
      set(HAVE_ALLOCA 1)
    endif()
    set(HAVE_M_PI 1)
    add_definitions(-D_USE_MATH_DEFINES) # needed for M_PI
    set(STDC_HEADERS 1)
  else()
    set(HAVE_LIBC TRUE)
    check_include_file(sys/types.h HAVE_SYS_TYPES_H)
    foreach(_HEADER
            stdio.h stdlib.h stddef.h stdarg.h malloc.h memory.h string.h
            strings.h inttypes.h stdint.h ctype.h math.h iconv.h signal.h)
      string(TOUPPER "HAVE_${_HEADER}" _UPPER)
      string(REPLACE "." "_" _HAVE_H ${_UPPER})
      check_include_file("${_HEADER}" ${_HAVE_H})
    endforeach()

    check_include_files("dlfcn.h;stdint.h;stddef.h;inttypes.h;stdlib.h;strings.h;string.h;float.h" STDC_HEADERS)
    check_type_size("size_t" SIZEOF_SIZE_T)
    check_symbol_exists(M_PI math.h HAVE_M_PI)
    # TODO: refine the mprotect check
    check_c_source_compiles("#include <sys/types.h>
                             #include <sys/mman.h>
                             int main() { }" HAVE_MPROTECT)
    foreach(_FN
            strtod malloc calloc realloc free getenv setenv putenv unsetenv
            qsort abs bcopy memset memcpy memmove memcmp strlen strlcpy strlcat
            strdup _strrev _strupr _strlwr strchr strrchr strstr itoa _ltoa
            _uitoa _ultoa strtol strtoul _i64toa _ui64toa strtoll strtoull
            atoi atof strcmp strncmp _stricmp strcasecmp _strnicmp strncasecmp
            vsscanf vsnprintf fseeko fseeko64 sigaction setjmp
            nanosleep sysconf sysctlbyname
            )
      string(TOUPPER ${_FN} _UPPER)
      set(_HAVEVAR "HAVE_${_UPPER}")
      check_function_exists("${_FN}" ${_HAVEVAR})
    endforeach()

    check_library_exists(m pow "" HAVE_LIBM)
    if(HAVE_LIBM)
      set(CMAKE_REQUIRED_LIBRARIES m)
      foreach(_FN
              atan atan2 ceil copysign cos cosf fabs floor log pow scalbn sin
              sinf sqrt sqrtf tan tanf acos asin)
        string(TOUPPER ${_FN} _UPPER)
        set(_HAVEVAR "HAVE_${_UPPER}")
        check_function_exists("${_FN}" ${_HAVEVAR})
      endforeach()
      set(CMAKE_REQUIRED_LIBRARIES)
      list(APPEND EXTRA_LIBS m)
    endif()

    check_library_exists(iconv iconv_open "" HAVE_LIBICONV)
    if(HAVE_LIBICONV)
      list(APPEND EXTRA_LIBS iconv)
      set(HAVE_ICONV 1)
    endif()

    if(NOT APPLE)
      check_include_file(alloca.h HAVE_ALLOCA_H)
      check_function_exists(alloca HAVE_ALLOCA)
    else()
      set(HAVE_ALLOCA_H 1)
      set(HAVE_ALLOCA 1)
    endif()

    check_struct_has_member("struct sigaction" "sa_sigaction" "signal.h" HAVE_SA_SIGACTION)
  endif()
endif()

# Enable/disable various subsystems of the SDL library
foreach(_SUB ${SDL_SUBSYSTEMS})
  string(TOUPPER ${_SUB} _OPT)
  if(NOT SDL_${_OPT})
    set(SDL_${_OPT}_DISABLED 1)
  endif()
endforeach()
if(SDL_JOYSTICK)
  file(GLOB JOYSTICK_SOURCES ${SDL2_SOURCE_DIR}/src/joystick/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${JOYSTICK_SOURCES})
endif()
if(SDL_HAPTIC)
  if(NOT SDL_JOYSTICK)
    # Haptic requires some private functions from the joystick subsystem.
    message_error("SDL_HAPTIC requires SDL_JOYSTICK, which is not enabled")
  endif()
  file(GLOB HAPTIC_SOURCES ${SDL2_SOURCE_DIR}/src/haptic/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${HAPTIC_SOURCES})
endif()
if(SDL_POWER)
  file(GLOB POWER_SOURCES ${SDL2_SOURCE_DIR}/src/power/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${POWER_SOURCES})
endif()
# TODO: in configure.in, the test for LOADSO and SDL_DLOPEN is a bit weird:
# if LOADSO is not wanted, SDL_LOADSO_DISABLED is set
# If however on Unix or APPLE dlopen() is detected via CheckDLOPEN(),
# SDL_LOADSO_DISABLED will not be set, regardless of the LOADSO settings

# General SDL subsystem options, valid for all platforms
if(SDL_AUDIO)
  # CheckDummyAudio/CheckDiskAudio - valid for all platforms
  if(DUMMYAUDIO)
    set(SDL_AUDIO_DRIVER_DUMMY 1)
    file(GLOB DUMMYAUDIO_SOURCES ${SDL2_SOURCE_DIR}/src/audio/dummy/*.c)
    set(SOURCE_FILES ${SOURCE_FILES} ${DUMMYAUDIO_SOURCES})
    set(HAVE_DUMMYAUDIO TRUE)
  endif()
  if(DISKAUDIO)
    set(SDL_AUDIO_DRIVER_DISK 1)
    file(GLOB DISKAUDIO_SOURCES ${SDL2_SOURCE_DIR}/src/audio/disk/*.c)
    set(SOURCE_FILES ${SOURCE_FILES} ${DISKAUDIO_SOURCES})
    set(HAVE_DISKAUDIO TRUE)
  endif()
endif()

if(SDL_DLOPEN)
  # Relevant for Unix/Darwin only
  if(UNIX OR APPLE)
    CheckDLOPEN()
  endif()
endif()

if(SDL_VIDEO)
  if(VIDEO_DUMMY)
    set(SDL_VIDEO_DRIVER_DUMMY 1)
    file(GLOB VIDEO_DUMMY_SOURCES ${SDL2_SOURCE_DIR}/src/video/dummy/*.c)
    set(SOURCE_FILES ${SOURCE_FILES} ${VIDEO_DUMMY_SOURCES})
    set(HAVE_VIDEO_DUMMY TRUE)
    set(HAVE_SDL_VIDEO TRUE)
  endif()
endif()

# Dummies
# configure.in does it differently:
# if not have X
#   if enable_X {  SDL_X_DISABLED = 1 }
#   [add dummy sources]
# so it always adds a dummy, without checking, if it was actually requested.
# This leads to missing internal references on building, since the
# src/X/*.c does not get included.
if(NOT HAVE_SDL_JOYSTICK)
  set(SDL_JOYSTICK_DISABLED 1)
  if(SDL_JOYSTICK AND NOT APPLE) # results in unresolved symbols on OSX

    file(GLOB JOYSTICK_SOURCES ${SDL2_SOURCE_DIR}/src/joystick/dummy/*.c)
    set(SOURCE_FILES ${SOURCE_FILES} ${JOYSTICK_SOURCES})
  endif()
endif()
if(NOT HAVE_SDL_HAPTIC)
  set(SDL_HAPTIC_DISABLED 1)
  file(GLOB HAPTIC_SOURCES ${SDL2_SOURCE_DIR}/src/haptic/dummy/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${HAPTIC_SOURCES})
endif()
if(NOT HAVE_SDL_LOADSO)
  set(SDL_LOADSO_DISABLED 1)
  file(GLOB LOADSO_SOURCES ${SDL2_SOURCE_DIR}/src/loadso/dummy/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${LOADSO_SOURCES})
endif()
if(NOT HAVE_SDL_FILESYSTEM)
  set(SDL_FILESYSTEM_DISABLED 1)
  file(GLOB FILESYSTEM_SOURCES ${SDL2_SOURCE_DIR}/src/filesystem/dummy/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${FILESYSTEM_SOURCES})
endif()

# We always need to have threads and timers around
if(NOT HAVE_SDL_THREADS)
  set(SDL_THREADS_DISABLED 1)
  file(GLOB THREADS_SOURCES ${SDL2_SOURCE_DIR}/src/thread/generic/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${THREADS_SOURCES})
endif()
if(NOT HAVE_SDL_TIMERS)
  set(SDL_TIMERS_DISABLED 1)
  file(GLOB TIMER_SOURCES ${SDL2_SOURCE_DIR}/src/timer/dummy/*.c)
  set(SOURCE_FILES ${SOURCE_FILES} ${TIMER_SOURCES})
endif()

configure_file("${SDL2_SOURCE_DIR}/include/SDL_config.h.cmake"
  "${SDL2_BINARY_DIR}/include/SDL_config.h")

# Prepare the flags and remove duplicates
if(EXTRA_LDFLAGS)
  list(REMOVE_DUPLICATES EXTRA_LDFLAGS)
endif()
if(EXTRA_LIBS)
  list(REMOVE_DUPLICATES EXTRA_LIBS)
endif()
if(EXTRA_CFLAGS)
  list(REMOVE_DUPLICATES EXTRA_CFLAGS)
endif()
listtostr(EXTRA_CFLAGS _EXTRA_CFLAGS)
set(EXTRA_CFLAGS ${_EXTRA_CFLAGS})

# Ensure that the extra cflags are used at compile time
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${EXTRA_CFLAGS}")

if(SDL_SHARED)
  add_library(SDL2 SHARED ${SOURCE_FILES})
  set_target_properties(SDL2 PROPERTIES
    VERSION ${SDL_VERSION}
    SOVERSION ${LT_REVISION}
    OUTPUT_NAME "SDL2")
  set(_INSTALL_LIBS "SDL2" ${_INSTALL_LIBS})
  target_link_libraries(SDL2 ${EXTRA_LIBS} ${EXTRA_LDFLAGS})
endif()

if(SDL_STATIC)
  set (BUILD_SHARED_LIBS FALSE)
  add_library(SDL2-static STATIC ${SOURCE_FILES})
  set_target_properties(SDL2-static PROPERTIES OUTPUT_NAME "SDL2")
  set(_INSTALL_LIBS "SDL2-static" ${_INSTALL_LIBS})
  target_link_libraries(SDL2-static ${EXTRA_LIBS} ${EXTRA_LDFLAGS})
endif()
