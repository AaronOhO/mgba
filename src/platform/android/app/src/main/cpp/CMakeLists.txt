# Copyright (c) 2026 Jeffrey Pfau
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
cmake_minimum_required(VERSION 3.22.1)
project(mgba_android C)

get_filename_component(MGBA_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../../.." ABSOLUTE)

set(LIBMGBA_ONLY ON CACHE BOOL "Build only the mGBA library" FORCE)
set(M_CORE_GBA ON CACHE BOOL "Build the Game Boy Advance core" FORCE)
set(M_CORE_GB OFF CACHE BOOL "Do not build the Game Boy core in the first Android release" FORCE)
set(BUILD_STATIC ON CACHE BOOL "Build mGBA as a static library" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "Do not build mGBA as a shared library" FORCE)
set(BUILD_LTO OFF CACHE BOOL "Keep Android debug builds predictable" FORCE)

add_subdirectory("${MGBA_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/mgba")

add_library(mgba-android SHARED mgba_android.c)
get_target_property(MGBA_COMPILE_DEFINITIONS mgba COMPILE_DEFINITIONS)
target_compile_definitions(mgba-android PRIVATE ${MGBA_COMPILE_DEFINITIONS})
target_link_libraries(mgba-android PRIVATE mgba log android)
target_compile_options(mgba-android PRIVATE -Wall -Wextra -Werror)
