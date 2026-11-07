cmake_minimum_required(VERSION 3.24)
project(liyue_seedgen C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CUBIOMES_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes)

# Generated loot tables: one translation unit per chest/barrel variant. The
# vendored cubiomes CMakeLists lists them explicitly; globbing keeps this list
# from drifting when the snapshot is updated.
file(GLOB LUA_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/lua/*.c)

file(GLOB LOOT_TABLE_SOURCES CONFIGURE_DEPENDS
    ${CUBIOMES_DIR}/loot/loot_tables/chests/*.c
    ${CUBIOMES_DIR}/loot/loot_tables/chests/trial_chambers/*.c
    ${CUBIOMES_DIR}/loot/loot_tables/barrels/*.c
)

add_library(cubiomes STATIC
    ${LOOT_TABLE_SOURCES}
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
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/cubiomes_details.c
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/underground_volume.c
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/ancient_city_layout.c
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl/axolotl_jni.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_bedrock.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_bedrock_jni.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_dungeon.c
    liyue_quad.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_flower.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_flower_jni.c
    ${CMAKE_CURRENT_SOURCE_DIR}/liyue_dungeon_jni.c
    liyue_quad_jni.c
    liyue_lua.c
    ${LUA_SOURCES}
    ${CUBIOMES_DIR}/loot/items.c
    ${CUBIOMES_DIR}/loot/loot_functions.c
    ${CUBIOMES_DIR}/loot/loot_table_context.c
    ${CUBIOMES_DIR}/loot/loot_table_parser.c
    ${CUBIOMES_DIR}/loot/loot_tables.c
    ${CUBIOMES_DIR}/loot/cjson/cJSON.c
)

target_include_directories(liyue_seedgen PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/axolotl
)

if(ANDROID)
    target_link_libraries(liyue_seedgen cubiomes m log)
else()
    # Host-side build (tools/verify-seedgen.sh). Only the JVM entry points are
    # needed, so AWT is not required; a headless JDK has no libjawt.
    find_package(JNI QUIET COMPONENTS JVM)
    if(NOT JNI_FOUND)
        find_package(JNI REQUIRED)
    endif()
    target_include_directories(liyue_seedgen PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(liyue_seedgen cubiomes m)
endif()
