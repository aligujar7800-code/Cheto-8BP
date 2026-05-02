cmake_minimum_required(VERSION 3.22.1)

project("cheto-native")

add_library(
    cheto-native
    SHARED
    memory_engine.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    cheto-native
    ${log-lib}
)
