cmake_minimum_required(VERSION 3.22.1)
project("odinvulkanlayer")

# Encontra o compilador Vulkan (glslc) nativo do Android NDK
set(GLSLC_PATH "${ANDROID_NDK}/shader-tools/${ANDROID_NDK_HOST_SYSTEM_NAME}/glslc")

# Configura as pastas dos shaders do Ludashi
set(SHADER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/shaders)
set(SPV_DIR ${CMAKE_CURRENT_BINARY_DIR}/spv)
file(MAKE_DIRECTORY ${SPV_DIR})

# Automação: Converte os .frag para binários .spv durante a build
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

# Cria a biblioteca partilhada da nossa Camada Vulkan
add_library(OdinVulkanLayer SHARED OdinVulkanLayer.cpp)

# Garante que os shaders compilados são injetados como dependências da build
add_custom_target(CompileShaders DEPENDS ${SPV_DIR}/window_sgsr.spv ${SPV_DIR}/window_postfx.spv)
add_dependencies(OdinVulkanLayer CompileShaders)

# Liga o log e o sistema gráfico Android à nossa biblioteca
target_link_libraries(OdinVulkanLayer android log vulkan)
