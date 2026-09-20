# Gera um cabeçalho C com o conteúdo binário de um .spv embutido como um array de uint32_t.
#
# PORQUÊ: a nossa camada Vulkan corre dentro do processo do JOGO, não do nosso. Não podemos
# confiar em ler os .spv de um caminho de ficheiro em disco em tempo de execução (o processo do
# jogo não tem o nosso AssetManager, e um caminho absoluto para a pasta da nossa app pode não ser
# legível/nem existir consoante o dispositivo). A solução robusta e usada por praticamente todas
# as camadas Vulkan de terceiros é embutir o bytecode SPIR-V diretamente na .so em tempo de
# compilação -- zero dependências de ficheiros em runtime.
#
# Uso: cmake -DSPV_IN=<ficheiro.spv> -DHEADER_OUT=<ficheiro.h> -DVAR_NAME=<nome> -P EmbedSpv.cmake

if(NOT SPV_IN OR NOT HEADER_OUT OR NOT VAR_NAME)
    message(FATAL_ERROR "EmbedSpv.cmake precisa de -DSPV_IN=, -DHEADER_OUT= e -DVAR_NAME=")
endif()

file(READ "${SPV_IN}" SPV_HEX HEX)

# SPIR-V é sempre um múltiplo de 4 bytes (array de uint32_t) -- agrupamos 8 dígitos hex (4 bytes)
# de cada vez, respeitando little-endian (é o que glslc/SPIR-V produzem nesta plataforma).
string(LENGTH "${SPV_HEX}" SPV_HEX_LEN)
math(EXPR WORD_COUNT "${SPV_HEX_LEN} / 8")

set(BODY "")
set(INDEX 0)
while(INDEX LESS WORD_COUNT)
    math(EXPR OFFSET "${INDEX} * 8")
    string(SUBSTRING "${SPV_HEX}" ${OFFSET} 8 WORD_HEX)
    string(SUBSTRING "${WORD_HEX}" 0 2 B0)
    string(SUBSTRING "${WORD_HEX}" 2 2 B1)
    string(SUBSTRING "${WORD_HEX}" 4 2 B2)
    string(SUBSTRING "${WORD_HEX}" 6 2 B3)
    string(APPEND BODY "0x${B3}${B2}${B1}${B0},")
    math(EXPR INDEX "${INDEX} + 1")
    if(INDEX GREATER 0)
        math(EXPR MOD "${INDEX} % 8")
        if(MOD EQUAL 0)
            string(APPEND BODY "\n    ")
        endif()
    endif()
endwhile()

file(WRITE "${HEADER_OUT}" "// Gerado automaticamente por EmbedSpv.cmake a partir de ${SPV_IN} -- não editar à mão.\n#pragma once\n#include <cstdint>\nstatic const uint32_t ${VAR_NAME}[] = {\n    ${BODY}\n};\nstatic const unsigned int ${VAR_NAME}_size = sizeof(${VAR_NAME});\n")
