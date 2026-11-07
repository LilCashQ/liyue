/*
 * Lua scripting for 里悦, modelled on cubiomes-viewer's script engine.
 *
 * The script gets a handful of globals (seed, version, mc) plus a small API:
 *
 *   print(...)                        add a line to the console
 *   emit(x, z, label)                 add a result row
 *   getBiomeAt(x, z)                  biome id (1:1) at (x, z), y = surface
 *   getBiomeAt(x, y, z)               biome id at an exact height
 *   getStructures(type, x0, z0, x1, z1)  array of { x, z }
 *   getStrongholds(count)             array of { x, z }
 *   slimeChunk(chunkX, chunkZ)        true when the chunk is a slime chunk
 *   biome("plains")                   biome ids by name, as a table `BIOME`
 *   STRUCTURE.village                 structure type ids, as a table `STRUCTURE`
 *
 * The script body runs first, then `main()` if it exists. Everything it emits
 * is collected and handed back to Kotlin as JSON.
 */

#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cubiomes/finders.h"
#include "cubiomes/generator.h"
#include "cubiomes/loot/cjson/cJSON.h"
#include "lua/lua.h"
#include "lua/lauxlib.h"
#include "lua/lualib.h"

int liyue_mc_version(int version);

typedef struct ScriptCtx
{
    Generator gen;
    int mc;
    int64_t seed;
    int dimension;
    cJSON *logs;
    cJSON *hits;
    char error[512];
} ScriptCtx;

static ScriptCtx *ctx_of(lua_State *L)
{
    lua_getfield(L, LUA_REGISTRYINDEX, "liyue_ctx");
    ScriptCtx *ctx = (ScriptCtx *)lua_touserdata(L, -1);
    lua_pop(L, 1);
    return ctx;
}

static void ensure_dimension(ScriptCtx *ctx, int dim)
{
    if (ctx->dimension == dim) return;
    if (dim == DIM_OVERWORLD || dim == DIM_NETHER || dim == DIM_END)
    {
        applySeed(&ctx->gen, dim, (uint64_t)ctx->seed);
        ctx->dimension = dim;
    }
}

static int l_print(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    int n = lua_gettop(L);
    luaL_Buffer buf;
    luaL_buffinit(L, &buf);
    for (int i = 1; i <= n; i++)
    {
        const char *text = luaL_tolstring(L, i, NULL);
        if (i > 1) luaL_addchar(&buf, '\t');
        luaL_addstring(&buf, text ? text : "");
        lua_pop(L, 1);
    }
    luaL_pushresult(&buf);
    const char *line = lua_tostring(L, -1);
    if (ctx && ctx->logs && line)
        cJSON_AddItemToArray(ctx->logs, cJSON_CreateString(line));
    lua_pop(L, 1);
    return 0;
}

static int l_emit(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    if (!ctx || !ctx->hits) return 0;
    int x = (int)luaL_optnumber(L, 1, 0);
    int z = (int)luaL_optnumber(L, 2, 0);
    const char *label = luaL_optstring(L, 3, "");
    cJSON *item = cJSON_CreateObject();
    cJSON_AddNumberToObject(item, "x", x);
    cJSON_AddNumberToObject(item, "z", z);
    cJSON_AddStringToObject(item, "label", label ? label : "");
    cJSON_AddItemToArray(ctx->hits, item);
    return 0;
}

static int l_get_biome(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    int n = lua_gettop(L);
    int x, z, y = 63;
    if (n >= 3)
    {
        x = (int)luaL_checknumber(L, 1);
        y = (int)luaL_checknumber(L, 2);
        z = (int)luaL_checknumber(L, 3);
    }
    else
    {
        x = (int)luaL_checknumber(L, 1);
        z = (int)luaL_checknumber(L, 2);
    }
    if (!ctx) { lua_pushinteger(L, 0); return 1; }
    ensure_dimension(ctx, DIM_OVERWORLD);
    int id = getBiomeAt(&ctx->gen, 1, x, y, z);
    lua_pushinteger(L, id);
    return 1;
}

static int l_get_structures(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    lua_newtable(L);
    if (!ctx) return 1;
    int styp = (int)luaL_checknumber(L, 1);
    int x0 = (int)luaL_checknumber(L, 2);
    int z0 = (int)luaL_checknumber(L, 3);
    int x1 = (int)luaL_checknumber(L, 4);
    int z1 = (int)luaL_checknumber(L, 5);
    if (x0 > x1) { int t = x0; x0 = x1; x1 = t; }
    if (z0 > z1) { int t = z0; z0 = z1; z1 = t; }
    if ((long)x1 - x0 > 65536L || (long)z1 - z0 > 65536L)
        return 1;

    StructureConfig sconf;
    if (!getStructureConfig(styp, ctx->mc, &sconf)) return 1;
    ensure_dimension(ctx, sconf.dim);

    double blocksPerRegion = sconf.regionSize * 16.0;
    int rx0 = (int)floor(x0 / blocksPerRegion);
    int rz0 = (int)floor(z0 / blocksPerRegion);
    int rx1 = (int)floor(x1 / blocksPerRegion);
    int rz1 = (int)floor(z1 / blocksPerRegion);

    int index = 1;
    for (int rz = rz0; rz <= rz1; rz++)
    {
        for (int rx = rx0; rx <= rx1; rx++)
        {
            Pos pos;
            if (!getStructurePos(styp, ctx->mc, (uint64_t)ctx->seed, rx, rz, &pos))
                continue;
            if (pos.x < x0 || pos.x > x1 || pos.z < z0 || pos.z > z1)
                continue;
            if (!isViableStructurePos(styp, &ctx->gen, pos.x, pos.z, 0))
                continue;
            if (ctx->mc >= MC_1_18 && !isViableStructureTerrain(styp, &ctx->gen, pos.x, pos.z))
                continue;
            lua_newtable(L);
            lua_pushinteger(L, pos.x);
            lua_setfield(L, -2, "x");
            lua_pushinteger(L, pos.z);
            lua_setfield(L, -2, "z");
            lua_rawseti(L, -2, index++);
        }
    }
    return 1;
}

static int l_get_strongholds(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    lua_newtable(L);
    if (!ctx) return 1;
    int count = (int)luaL_optnumber(L, 1, 3);
    if (count < 1) count = 1;
    if (count > 32) count = 32;
    ensure_dimension(ctx, DIM_OVERWORLD);
    StrongholdIter sh;
    initFirstStronghold(&sh, ctx->mc, (uint64_t)ctx->seed);
    for (int i = 0; i < count; i++)
    {
        if (nextStronghold(&sh, &ctx->gen) <= 0) break;
        lua_newtable(L);
        lua_pushinteger(L, sh.pos.x);
        lua_setfield(L, -2, "x");
        lua_pushinteger(L, sh.pos.z);
        lua_setfield(L, -2, "z");
        lua_rawseti(L, -2, i + 1);
    }
    return 1;
}

static int l_slime_chunk(lua_State *L)
{
    ScriptCtx *ctx = ctx_of(L);
    int cx = (int)luaL_checknumber(L, 1);
    int cz = (int)luaL_checknumber(L, 2);
    int slime = ctx ? isSlimeChunk((uint64_t)ctx->seed, cx, cz) : 0;
    lua_pushboolean(L, slime);
    return 1;
}

static void set_table_int(lua_State *L, const char *table, const char *key, int value)
{
    lua_getglobal(L, table);
    if (!lua_istable(L, -1))
    {
        lua_pop(L, 1);
        lua_newtable(L);
        lua_pushvalue(L, -1);
        lua_setglobal(L, table);
    }
    lua_pushinteger(L, value);
    lua_setfield(L, -2, key);
    lua_pop(L, 1);
}

static void register_api(lua_State *L)
{
    lua_pushcfunction(L, l_print);
    lua_setglobal(L, "print");
    lua_pushcfunction(L, l_emit);
    lua_setglobal(L, "emit");
    lua_pushcfunction(L, l_get_biome);
    lua_setglobal(L, "getBiomeAt");
    lua_pushcfunction(L, l_get_structures);
    lua_setglobal(L, "getStructures");
    lua_pushcfunction(L, l_get_strongholds);
    lua_setglobal(L, "getStrongholds");
    lua_pushcfunction(L, l_slime_chunk);
    lua_setglobal(L, "slimeChunk");

    lua_newtable(L);
    lua_setglobal(L, "BIOME");
    lua_newtable(L);
    lua_setglobal(L, "STRUCTURE");

    static const struct { const char *name; int value; } biomes[] = {
        {"ocean", ocean}, {"plains", plains}, {"desert", desert},
        {"mountains", mountains}, {"forest", forest}, {"taiga", taiga},
        {"swamp", swamp}, {"river", river}, {"nether_wastes", nether_wastes},
        {"the_end", the_end}, {"jungle", jungle}, {"birch_forest", birch_forest},
        {"dark_forest", roofedForest}, {"snowy_tundra", icePlains},
        {"badlands", badlands}, {"savanna", savanna}, {"beach", beach},
        {"mushroom_fields", mushroomIsland}, {"lush_caves", lush_caves},
        {"dripstone_caves", dripstone_caves}, {"deep_dark", deep_dark},
        {"cherry_grove", cherry_grove}, {"mangrove_swamp", mangrove_swamp},
    };
    for (size_t i = 0; i < sizeof(biomes) / sizeof(biomes[0]); i++)
        set_table_int(L, "BIOME", biomes[i].name, biomes[i].value);

    static const struct { const char *name; int value; } structs[] = {
        {"village", Village}, {"desert_pyramid", Desert_Pyramid},
        {"jungle_temple", Jungle_Temple}, {"swamp_hut", Swamp_Hut},
        {"igloo", Igloo}, {"monument", Monument}, {"mansion", Mansion},
        {"outpost", Outpost}, {"ruined_portal", Ruined_Portal},
        {"shipwreck", Shipwreck}, {"treasure", Treasure},
        {"ocean_ruin", Ocean_Ruin}, {"mineshaft", Mineshaft},
        {"fortress", Fortress}, {"bastion", Bastion},
        {"end_city", End_City}, {"ancient_city", Ancient_City},
        {"trail_ruins", Trail_Ruins}, {"trial_chambers", Trial_Chambers},
    };
    for (size_t i = 0; i < sizeof(structs) / sizeof(structs[0]); i++)
        set_table_int(L, "STRUCTURE", structs[i].name, structs[i].value);
}

char *liyue_run_script(const char *script, int64_t seed, int mc, int *ok_out)
{
    lua_State *L = luaL_newstate();
    if (!L) return NULL;
    luaL_openlibs(L);

    ScriptCtx ctx;
    memset(&ctx, 0, sizeof(ctx));
    ctx.mc = mc;
    ctx.seed = seed;
    ctx.dimension = -1;
    ctx.logs = cJSON_CreateArray();
    ctx.hits = cJSON_CreateArray();

    setupGenerator(&ctx.gen, mc, 0);
    applySeed(&ctx.gen, DIM_OVERWORLD, (uint64_t)seed);
    ctx.dimension = DIM_OVERWORLD;

    lua_pushlightuserdata(L, &ctx);
    lua_setfield(L, LUA_REGISTRYINDEX, "liyue_ctx");

    register_api(L);

    lua_pushinteger(L, (lua_Integer)seed);
    lua_setglobal(L, "seed");
    lua_pushinteger(L, mc);
    lua_setglobal(L, "mc");

    int ok = 1;
    if (luaL_loadbuffer(L, script, strlen(script), "script") != LUA_OK)
    {
        snprintf(ctx.error, sizeof(ctx.error), "%s", lua_tostring(L, -1) ? lua_tostring(L, -1) : "语法错误");
        ok = 0;
    }
    else if (lua_pcall(L, 0, 0, 0) != LUA_OK)
    {
        snprintf(ctx.error, sizeof(ctx.error), "%s", lua_tostring(L, -1) ? lua_tostring(L, -1) : "运行失败");
        ok = 0;
    }
    else
    {
        lua_getglobal(L, "main");
        if (lua_isfunction(L, -1))
        {
            if (lua_pcall(L, 0, 0, 0) != LUA_OK)
            {
                snprintf(ctx.error, sizeof(ctx.error), "%s",
                         lua_tostring(L, -1) ? lua_tostring(L, -1) : "main() 运行失败");
                ok = 0;
            }
        }
        else
        {
            lua_pop(L, 1);
        }
    }

    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", ok);
    if (ok) cJSON_AddNullToObject(root, "error");
    else cJSON_AddStringToObject(root, "error", ctx.error);
    cJSON_AddItemToObject(root, "logs", ctx.logs);
    cJSON_AddItemToObject(root, "hits", ctx.hits);
    char *json = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    lua_close(L);
    if (ok_out) *ok_out = ok;
    return json;
}

JNIEXPORT jstring JNICALL
Java_com_liyue_mctoolbox_seedgen_NativeSeedgen_runScript(
        JNIEnv *env, jobject thiz,
        jstring script, jlong seed, jint version)
{
    (void)thiz;
    if (!script) return NULL;
    const char *text = (*env)->GetStringUTFChars(env, script, NULL);
    if (!text) return NULL;
    int mc = liyue_mc_version(version);
    char *json = liyue_run_script(text, (int64_t)seed, mc, NULL);
    (*env)->ReleaseStringUTFChars(env, script, text);
    if (!json) return NULL;
    jstring out = (*env)->NewStringUTF(env, json);
    cJSON_free(json);
    return out;
}
