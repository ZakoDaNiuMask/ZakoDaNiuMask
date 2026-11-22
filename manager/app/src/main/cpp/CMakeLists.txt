# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

add_library(kernelsu
        SHARED
        jni.c
        ksu.c
        legacy.c
)

# `.` 提供 cpp 目录内的头文件；仓库根的 uapi/ 提供 `uapi/*.h`。
# 显式指向仓库根，避免依赖 cpp/uapi 符号链接（Windows 未启用开发者模式时无法检出符号链接）。
target_include_directories(kernelsu PRIVATE . ${CMAKE_CURRENT_SOURCE_DIR}/../../../../..)

find_library(log-lib log)

target_link_libraries(
        kernelsu
        ${log-lib}
)

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(adbroot SHARED adbroot.cc)
target_link_libraries(adbroot ${log-lib})
