cmake_minimum_required(VERSION 3.22)
project(liyue_seedgen C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CUBIOMES_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes)

add_library(cubiomes STATIC
    ${CUBIOMES_DIR}/biomenoise.c
    ${CUBIOMES_DIR}/biomes.c
    ${CUBIOMES_DIR}/carver.c
    ${CUBIOMES_DIR}/finders.c
    ${CUBIOMES_DIR}/generator.c
    ${CUBIOMES_DIR}/layers.c
    ${CUBIOMES_DIR}/noise.c
    ${CUBIOMES_DIR}/quadbase.c
    ${CUBIOMES_DIR}/terrainnoise.c
    ${CUBIOMES_DIR}/util.c
    ${CUBIOMES_DIR}/xradv.c
    ${CUBIOMES_DIR}/features/abandoned_camp.c
    ${CUBIOMES_DIR}/features/end_city.c
    ${CUBIOMES_DIR}/features/fortress.c
    ${CUBIOMES_DIR}/features/ore.c
    ${CUBIOMES_DIR}/features/stronghold.c
)

target_include_directories(cubiomes PUBLIC ${CUBIOMES_DIR})
target_compile_options(cubiomes PRIVATE -O2 -fno-strict-aliasing -Wall)
set_target_properties(cubiomes PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(liyue_seedgen SHARED
    liyue_seedgen.c
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/cubiomes_bridge.c
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/axolotl_jni.c
)

target_include_directories(liyue_seedgen PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl
)

if(ANDROID)
    target_link_libraries(liyue_seedgen cubiomes m log)
else()
    find_package(JNI REQUIRED)
    target_include_directories(liyue_seedgen PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(liyue_seedgen cubiomes m)
endif()
