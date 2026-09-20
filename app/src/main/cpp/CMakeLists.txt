cmake_minimum_required(VERSION 3.22.1)
project("odinvulkanlayer")

# Encontra o compilador Vulkan (glslc) nativo do Android NDK
set(GLSLC_PATH "${ANDROID_NDK}/shader-tools/${ANDROID_NDK_HOST_SYSTEM_NAME}/glslc")

# Configura as pastas dos shaders do Ludashi
set(SHADER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/shaders)
set(SPV_DIR ${CMAKE_CURRENT_BINARY_DIR}/spv)
set(GEN_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${SPV_DIR})
file(MAKE_DIRECTORY ${GEN_DIR})

# --- Passo 1: .frag/.vert -> .spv (como já estava) ---------------------------------------------
add_custom_command(
        OUTPUT ${SPV_DIR}/window_sgsr.spv
        COMMAND ${GLSLC_PATH} -c ${SHADER_DIR}/window_sgsr.frag -o ${SPV_DIR}/window_sgsr.spv
        DEPENDS ${SHADER_DIR}/window_sgsr.frag
)

add_custom_command(
        OUTPUT ${SPV_DIR}/window_postfx.spv
        COMMAND ${GLSLC_PATH} -c ${SHADER_DIR}/window_postfx.frag -o ${SPV_DIR}/window_postfx.spv
        DEPENDS ${SHADER_DIR}/window_postfx.frag
)

add_custom_command(
        OUTPUT ${SPV_DIR}/fullscreen.spv
        COMMAND ${GLSLC_PATH} -c ${SHADER_DIR}/fullscreen.vert -o ${SPV_DIR}/fullscreen.spv
        DEPENDS ${SHADER_DIR}/fullscreen.vert
)

# --- Passo 2: .spv -> .h (bytecode embutido na .so, ver EmbedSpv.cmake para o porquê) -----------
add_custom_command(
        OUTPUT ${GEN_DIR}/shader_sgsr.h
        COMMAND ${CMAKE_COMMAND} -DSPV_IN=${SPV_DIR}/window_sgsr.spv -DHEADER_OUT=${GEN_DIR}/shader_sgsr.h -DVAR_NAME=g_spv_sgsr -P ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
        DEPENDS ${SPV_DIR}/window_sgsr.spv ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
)
add_custom_command(
        OUTPUT ${GEN_DIR}/shader_postfx.h
        COMMAND ${CMAKE_COMMAND} -DSPV_IN=${SPV_DIR}/window_postfx.spv -DHEADER_OUT=${GEN_DIR}/shader_postfx.h -DVAR_NAME=g_spv_postfx -P ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
        DEPENDS ${SPV_DIR}/window_postfx.spv ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
)
add_custom_command(
        OUTPUT ${GEN_DIR}/shader_fullscreen.h
        COMMAND ${CMAKE_COMMAND} -DSPV_IN=${SPV_DIR}/fullscreen.spv -DHEADER_OUT=${GEN_DIR}/shader_fullscreen.h -DVAR_NAME=g_spv_fullscreen -P ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
        DEPENDS ${SPV_DIR}/fullscreen.spv ${CMAKE_CURRENT_SOURCE_DIR}/EmbedSpv.cmake
)

add_custom_target(CompileShaders DEPENDS
        ${GEN_DIR}/shader_sgsr.h
        ${GEN_DIR}/shader_postfx.h
        ${GEN_DIR}/shader_fullscreen.h
)

# --- A biblioteca da camada ----------------------------------------------------------------------
# Nome IMPORTANTE: o loader Vulkan on-device do Android (o mesmo mecanismo usado por
# `debug.vulkan.layers`/`debug.vulkan.layer.dir`) descobre camadas de terceiros pelo prefixo
# "libVkLayer_" no nome do ficheiro .so -- por isso o alvo chama-se `VkLayer_OdinHub`, não
# `OdinVulkanLayer` como antes. Isto já não é carregado pelo nosso próprio processo via
# System.loadLibrary (ver GraphicsLayerManager.kt/AUDIT) -- é o processo do JOGO que o carrega.
add_library(VkLayer_OdinHub SHARED OdinVulkanLayer.cpp)

target_include_directories(VkLayer_OdinHub PRIVATE ${GEN_DIR})

add_dependencies(VkLayer_OdinHub CompileShaders)

target_link_libraries(VkLayer_OdinHub android log vulkan)
