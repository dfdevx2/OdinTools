// Camada Vulkan real do Odin Hub -- interceta o vkQueuePresentKHR de um JOGO (não do nosso
// processo) para desenhar, por cima do frame já renderizado, um pass de pós-processamento
// (ReShade-style, `window_postfx.frag`) ou de upscaling/nitidez (SGSR, `window_sgsr.frag`).
//
// Esta reescrita substitui a versão anterior, que era só andaimes: `Odin_vkQueuePresentKHR`
// devolvia `VK_SUCCESS` sem tocar em nada, e a "ponte" JNI (`VulkanNativeBridge`) escrevia
// variáveis globais no processo ERRADO -- o nosso, não o do jogo, onde esta .so é carregada pelo
// loader Vulkan do sistema. Ver OdinLayerConfig.h para a correção desse problema (configuração via
// propriedades de sistema, que atravessam processos; ver GraphicsLayerManager.kt do lado Kotlin).
//
// SIMPLIFICAÇÕES DELIBERADAS DESTA PRIMEIRA VERSÃO (documentadas para quando testares no
// dispositivo e algo não bater certo):
//  1. SGSR e o pass de ReShade são ALTERNATIVOS por frame, não encadeados -- se SGSR estiver
//     ligado, corre SGSR; senão, se houver um efeito ReShade escolhido (!= 0), corre esse; senão,
//     não faz nada extra (caminho rápido, sem overhead quando tudo está desligado). Encadear os
//     dois exigiria uma segunda imagem intermédia e complica a primeira validação em hardware
//     real -- fica como evolução natural depois de confirmarmos que o pass único funciona.
//  2. LSFG (geração de frames) ainda não gera frames de verdade aqui -- é um algoritmo
//     (optical flow multi-nível) grande demais para escrever às cegas sem conseguir compilar nem
//     testar. A configuração (on/off, multiplicador) já chega a este ficheiro via propriedade de
//     sistema (ver `cfg.lsfgEnabled`), mas por agora só regista um aviso no logcat -- ver
//     AUDIT_PARTE5.md para o plano de a implementar a seguir, depois de validarmos que esta
//     camada carrega e o pass único funciona.
//  3. Assume-se que a família de fila pedida em `pCreateInfo->pQueueCreateInfos[0]` de
//     `vkCreateDevice` suporta graphics+present -- verdade para a esmagadora maioria dos motores
//     em Android/Adreno (uma única fila universal). Se um jogo específico não bater com isto, o
//     `LOGE` no `vkCreateDevice` vai mostrar isso no logcat.

#include <vulkan/vulkan.h>
#include "OdinVkLayerCompat.h"
#include <android/log.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <memory>

#include "shader_fullscreen.h"
#include "shader_postfx.h"
#include "shader_sgsr.h"
#include "OdinLayerConfig.h"

#define LOG_TAG "OdinHubLayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VK_LAYER_EXPORT extern "C" __attribute__((visibility("default")))

namespace {

odin::ConfigReader g_config;

// -------------------------------------------------------------------------------------------
// Auto-exclusão: NUNCA processar SGSR/ReShade/LSFG dentro do processo da própria Odin Hub.
// -------------------------------------------------------------------------------------------
// BUG GRAVE encontrado ao analisar o logcat do utilizador depois da Parte 5: o Android moderno
// desenha a própria UI da app (Compose/HWUI) via Vulkan por baixo dos panos, então QUALQUER
// processo que crie uma VkInstance carrega esta camada -- incluindo a própria Odin Hub, não só
// os jogos. `debug.vulkan.layers`/`debug.vulkan.layer.dir` são propriedades GLOBAIS do sistema,
// não há como restringi-las a um único processo a partir de fora, e um valor definido para um
// jogo pode ficar "preso" (ex.: a app foi forçada a fechar antes do
// `ForegroundAppWatcherService` conseguir chamar `disableLayer()` ao voltar à home). O resultado
// visto no aparelho real do utilizador: a própria Odin Hub abria, entrava em ecrã cinza e depois
// preto para sempre, sem crash nenhum -- o pipeline de SGSR/ReShade (código nunca antes testado
// em hardware real) estava a processar a interface da PRÓPRIA app e nalgum ponto bloqueava a
// thread de render (esperar por uma fence, por exemplo) sem nunca devolver.
//
// Correção definitiva, independente de qualquer corrida de `setprop`: a camada lê o próprio
// nome de processo via `/proc/self/cmdline` UMA VEZ, ao carregar, e se ele corresponder ao
// applicationId da Odin Hub (`com.dfdx047.odinhub`, de `app/build.gradle.kts`), marca-se como
// "processo próprio" -- `ProcessSwapchainPresent` então recusa-se SEMPRE a processar qualquer
// efeito nesse processo, não importa o que as propriedades `debug.odinhub.*` digam. A Odin Hub
// nunca deve aplicar os seus próprios efeitos a si mesma; só aos jogos.
bool DetectIsOwnProcess() {
    FILE *f = fopen("/proc/self/cmdline", "r");
    if (!f) return false;
    char buf[256] = {0};
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    if (n == 0) return false;
    buf[n] = '\0';
    // cmdline separa argumentos com '\0'; o primeiro é o nome do processo (normalmente igual
    // ao applicationId para o processo principal, ou "applicationId:algo" para um secundário).
    return strncmp(buf, "com.dfdx047.odinhub", strlen("com.dfdx047.odinhub")) == 0;
}

const bool g_isOwnProcess = DetectIsOwnProcess();

// -------------------------------------------------------------------------------------------
// Estado por VkDevice / por VkSwapchainKHR
// -------------------------------------------------------------------------------------------

struct SwapchainData {
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat format = VK_FORMAT_UNDEFINED;
    VkExtent2D extent{};

    std::vector<VkImage> images;             // imagens reais do swapchain (as que são apresentadas)
    std::vector<VkImageView> targetViews;    // vistas dessas imagens, como color attachment
    std::vector<VkFramebuffer> framebuffers;

    // Uma cópia "scratch" POR imagem do swapchain (não partilhada) -- evita condições de corrida
    // entre índices de imagem diferentes em voo ao mesmo tempo (double/triple buffering).
    std::vector<VkImage> scratchImages;
    std::vector<VkDeviceMemory> scratchMemories;
    std::vector<VkImageView> scratchViews;
    std::vector<VkDescriptorSet> descSets;

    std::vector<VkCommandBuffer> cmdBuffers;
    std::vector<VkFence> fences;             // sinaliza quando o cmdBuffer[i] pode ser reciclado
    std::vector<VkSemaphore> renderDoneSemaphores;
    std::vector<bool> fenceEverSubmitted;

    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkSampler sampler = VK_NULL_HANDLE;
    VkDescriptorSetLayout descSetLayout = VK_NULL_HANDLE;
    VkDescriptorPool descPool = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkShaderModule vertModule = VK_NULL_HANDLE;
    VkShaderModule postfxModule = VK_NULL_HANDLE;
    VkShaderModule sgsrModule = VK_NULL_HANDLE;
    VkPipeline postfxPipeline = VK_NULL_HANDLE;
    VkPipeline sgsrPipeline = VK_NULL_HANDLE;
    VkCommandPool cmdPool = VK_NULL_HANDLE;

    bool ready = false; // false se a criação de recursos falhou -- nesse caso, presenteamos sem efeito
};

struct DeviceData {
    VkDevice device = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    PFN_vkGetDeviceProcAddr gdpa = nullptr;
    uint32_t queueFamilyIndex = 0;

    PFN_vkDestroyDevice DestroyDevice = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties = nullptr;

    PFN_vkCreateSwapchainKHR CreateSwapchainKHR = nullptr;
    PFN_vkDestroySwapchainKHR DestroySwapchainKHR = nullptr;
    PFN_vkGetSwapchainImagesKHR GetSwapchainImagesKHR = nullptr;
    PFN_vkQueuePresentKHR QueuePresentKHR = nullptr;

    PFN_vkCreateImage CreateImage = nullptr;
    PFN_vkDestroyImage DestroyImage = nullptr;
    PFN_vkAllocateMemory AllocateMemory = nullptr;
    PFN_vkFreeMemory FreeMemory = nullptr;
    PFN_vkBindImageMemory BindImageMemory = nullptr;
    PFN_vkGetImageMemoryRequirements GetImageMemoryRequirements = nullptr;
    PFN_vkCreateImageView CreateImageView = nullptr;
    PFN_vkDestroyImageView DestroyImageView = nullptr;
    PFN_vkCreateRenderPass CreateRenderPass = nullptr;
    PFN_vkDestroyRenderPass DestroyRenderPass = nullptr;
    PFN_vkCreateFramebuffer CreateFramebuffer = nullptr;
    PFN_vkDestroyFramebuffer DestroyFramebuffer = nullptr;
    PFN_vkCreateShaderModule CreateShaderModule = nullptr;
    PFN_vkDestroyShaderModule DestroyShaderModule = nullptr;
    PFN_vkCreatePipelineLayout CreatePipelineLayout = nullptr;
    PFN_vkDestroyPipelineLayout DestroyPipelineLayout = nullptr;
    PFN_vkCreateGraphicsPipelines CreateGraphicsPipelines = nullptr;
    PFN_vkDestroyPipeline DestroyPipeline = nullptr;
    PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout = nullptr;
    PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout = nullptr;
    PFN_vkCreateDescriptorPool CreateDescriptorPool = nullptr;
    PFN_vkDestroyDescriptorPool DestroyDescriptorPool = nullptr;
    PFN_vkAllocateDescriptorSets AllocateDescriptorSets = nullptr;
    PFN_vkUpdateDescriptorSets UpdateDescriptorSets = nullptr;
    PFN_vkCreateSampler CreateSampler = nullptr;
    PFN_vkDestroySampler DestroySampler = nullptr;
    PFN_vkCreateCommandPool CreateCommandPool = nullptr;
    PFN_vkDestroyCommandPool DestroyCommandPool = nullptr;
    PFN_vkAllocateCommandBuffers AllocateCommandBuffers = nullptr;
    PFN_vkFreeCommandBuffers FreeCommandBuffers = nullptr;
    PFN_vkResetCommandBuffer ResetCommandBuffer = nullptr;
    PFN_vkBeginCommandBuffer BeginCommandBuffer = nullptr;
    PFN_vkEndCommandBuffer EndCommandBuffer = nullptr;
    PFN_vkCmdPipelineBarrier CmdPipelineBarrier = nullptr;
    PFN_vkCmdCopyImage CmdCopyImage = nullptr;
    PFN_vkCmdBeginRenderPass CmdBeginRenderPass = nullptr;
    PFN_vkCmdEndRenderPass CmdEndRenderPass = nullptr;
    PFN_vkCmdBindPipeline CmdBindPipeline = nullptr;
    PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets = nullptr;
    PFN_vkCmdPushConstants CmdPushConstants = nullptr;
    PFN_vkCmdSetViewport CmdSetViewport = nullptr;
    PFN_vkCmdSetScissor CmdSetScissor = nullptr;
    PFN_vkCmdDraw CmdDraw = nullptr;
    PFN_vkCreateFence CreateFence = nullptr;
    PFN_vkDestroyFence DestroyFence = nullptr;
    PFN_vkWaitForFences WaitForFences = nullptr;
    PFN_vkResetFences ResetFences = nullptr;
    PFN_vkCreateSemaphore CreateSemaphore = nullptr;
    PFN_vkDestroySemaphore DestroySemaphore = nullptr;
    PFN_vkQueueSubmit QueueSubmit = nullptr;
    PFN_vkDeviceWaitIdle DeviceWaitIdle = nullptr;

    std::unordered_map<VkSwapchainKHR, std::unique_ptr<SwapchainData>> swapchains;
};

struct InstanceData {
    VkInstance instance = VK_NULL_HANDLE;
    PFN_vkGetInstanceProcAddr gipa = nullptr;
    PFN_vkDestroyInstance DestroyInstance = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties = nullptr;
};

std::mutex g_mutex;
std::unordered_map<void *, std::unique_ptr<InstanceData>> g_instances;
std::unordered_map<void *, std::unique_ptr<DeviceData>> g_devices;

InstanceData *FindInstanceData(void *dispatchableHandle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_instances.find(ODIN_DISPATCH_KEY(dispatchableHandle));
    return it == g_instances.end() ? nullptr : it->second.get();
}

DeviceData *FindDeviceData(void *dispatchableHandle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(ODIN_DISPATCH_KEY(dispatchableHandle));
    return it == g_devices.end() ? nullptr : it->second.get();
}

// IMPORTANTE: os dois shaders declaram blocos `push_constant` com ORDENS DE CAMPOS DIFERENTES
// (window_postfx.frag: ndc*, effectId, sharpness, resW, resH -- 32 bytes; window_sgsr.frag: ndc*,
// useTexAlpha, invSrc*, src*, effectId, resW, sharpness -- 48 bytes). O GLSL calcula os offsets de
// cada campo pela ORDEM DE DECLARAÇÃO dentro do seu PRÓPRIO bloco, por isso não dá para usar uma
// única struct C++ partilhada para os dois -- preenchemos o layout exacto de cada um consoante a
// pipeline ativa nesse frame (só uma está ativa de cada vez, ver ProcessSwapchainPresent). O
// VkPushConstantRange do pipeline layout usa o maior dos dois (48 bytes, ver
// CreateSwapchainResources) como limite superior -- está sempre correcto enviar menos bytes do
// que o intervalo declarado, nunca mais.
struct PostfxPushConstants {
    float ndcX0 = -1.0f, ndcY0 = -1.0f, ndcX1 = 1.0f, ndcY1 = 1.0f;
    int32_t effectId = 0;
    float sharpness = 0.6f;
    float resW = 0, resH = 0;
};
struct SgsrPushConstants {
    float ndcX0 = -1.0f, ndcY0 = -1.0f, ndcX1 = 1.0f, ndcY1 = 1.0f;
    int32_t useTexAlpha = 0;
    float invSrcW = 0, invSrcH = 0, srcW = 0, srcH = 0;
    int32_t effectId = 0;
    float resW = 0, sharpness = 0.5f;
};
constexpr size_t kMaxPushConstantsSize = sizeof(SgsrPushConstants); // 48 bytes, o maior dos dois

uint32_t FindMemoryType(DeviceData *d, uint32_t typeBits, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memProps{};
    d->GetPhysicalDeviceMemoryProperties(d->physicalDevice, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) && (memProps.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    return 0; // Sem correspondência exacta -- 0 é o pior caso mas evita crash aqui; ver logcat.
}

// -------------------------------------------------------------------------------------------
// Criação/destruição de recursos por swapchain
// -------------------------------------------------------------------------------------------

void DestroySwapchainResources(DeviceData *d, SwapchainData *sc) {
    if (!sc) return;
    for (auto fb : sc->framebuffers) if (fb) d->DestroyFramebuffer(d->device, fb, nullptr);
    for (auto v : sc->targetViews) if (v) d->DestroyImageView(d->device, v, nullptr);
    for (auto v : sc->scratchViews) if (v) d->DestroyImageView(d->device, v, nullptr);
    for (auto img : sc->scratchImages) if (img) d->DestroyImage(d->device, img, nullptr);
    for (auto mem : sc->scratchMemories) if (mem) d->FreeMemory(d->device, mem, nullptr);
    for (auto f : sc->fences) if (f) d->DestroyFence(d->device, f, nullptr);
    for (auto s : sc->renderDoneSemaphores) if (s) d->DestroySemaphore(d->device, s, nullptr);
    if (!sc->cmdBuffers.empty() && sc->cmdPool) {
        d->FreeCommandBuffers(d->device, sc->cmdPool, (uint32_t) sc->cmdBuffers.size(), sc->cmdBuffers.data());
    }
    if (sc->cmdPool) d->DestroyCommandPool(d->device, sc->cmdPool, nullptr);
    if (sc->postfxPipeline) d->DestroyPipeline(d->device, sc->postfxPipeline, nullptr);
    if (sc->sgsrPipeline) d->DestroyPipeline(d->device, sc->sgsrPipeline, nullptr);
    if (sc->pipelineLayout) d->DestroyPipelineLayout(d->device, sc->pipelineLayout, nullptr);
    if (sc->postfxModule) d->DestroyShaderModule(d->device, sc->postfxModule, nullptr);
    if (sc->sgsrModule) d->DestroyShaderModule(d->device, sc->sgsrModule, nullptr);
    if (sc->vertModule) d->DestroyShaderModule(d->device, sc->vertModule, nullptr);
    if (sc->descPool) d->DestroyDescriptorPool(d->device, sc->descPool, nullptr);
    if (sc->descSetLayout) d->DestroyDescriptorSetLayout(d->device, sc->descSetLayout, nullptr);
    if (sc->sampler) d->DestroySampler(d->device, sc->sampler, nullptr);
    if (sc->renderPass) d->DestroyRenderPass(d->device, sc->renderPass, nullptr);
}

VkShaderModule LoadShaderModule(DeviceData *d, const uint32_t *code, size_t sizeBytes) {
    VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    ci.codeSize = sizeBytes;
    ci.pCode = code;
    VkShaderModule module = VK_NULL_HANDLE;
    VkResult r = d->CreateShaderModule(d->device, &ci, nullptr, &module);
    if (r != VK_SUCCESS) {
        LOGE("LoadShaderModule: vkCreateShaderModule falhou (%d)", r);
        return VK_NULL_HANDLE;
    }
    return module;
}

// Cria TODOS os recursos de uma vez para uma swapchain nova: render pass, sampler, descriptor
// set layout/pool, pipeline layout, as duas pipelines (postfx e sgsr, partilhando o mesmo vertex
// shader/layout), e por imagem: view alvo, framebuffer, imagem "scratch" + memória + view,
// descriptor set, command buffer, fence e semáforo.
bool CreateSwapchainResources(DeviceData *d, SwapchainData *sc) {
    // --- Render pass: um único color attachment (a própria imagem do swapchain), sem depth. ---
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = sc->format;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE; // o shader escreve o ecrã inteiro
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorRef{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorRef;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
    dependency.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;

    VkRenderPassCreateInfo rpCi{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    rpCi.attachmentCount = 1;
    rpCi.pAttachments = &colorAttachment;
    rpCi.subpassCount = 1;
    rpCi.pSubpasses = &subpass;
    rpCi.dependencyCount = 1;
    rpCi.pDependencies = &dependency;
    if (d->CreateRenderPass(d->device, &rpCi, nullptr, &sc->renderPass) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreateRenderPass falhou");
        return false;
    }

    // --- Sampler para ler a imagem "scratch" (a cópia do frame original) no shader. -------------
    VkSamplerCreateInfo sampCi{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    sampCi.magFilter = VK_FILTER_LINEAR;
    sampCi.minFilter = VK_FILTER_LINEAR;
    sampCi.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sampCi.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sampCi.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sampCi.maxLod = 0.25f; // os shaders usam textureLod/textureGather em nível 0 só
    if (d->CreateSampler(d->device, &sampCi, nullptr, &sc->sampler) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreateSampler falhou");
        return false;
    }

    // --- Descriptor set layout: binding 0 = combined image sampler (igual nos dois shaders). ---
    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo dslCi{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    dslCi.bindingCount = 1;
    dslCi.pBindings = &binding;
    if (d->CreateDescriptorSetLayout(d->device, &dslCi, nullptr, &sc->descSetLayout) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreateDescriptorSetLayout falhou");
        return false;
    }

    uint32_t imageCount = (uint32_t) sc->images.size();

    VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, imageCount};
    VkDescriptorPoolCreateInfo poolCi{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    poolCi.maxSets = imageCount;
    poolCi.poolSizeCount = 1;
    poolCi.pPoolSizes = &poolSize;
    if (d->CreateDescriptorPool(d->device, &poolCi, nullptr, &sc->descPool) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreateDescriptorPool falhou");
        return false;
    }

    // --- Pipeline layout: 1 descriptor set + push constants partilhados (ver PushConstants). ---
    VkPushConstantRange pcRange{VK_SHADER_STAGE_FRAGMENT_BIT, 0, (uint32_t) kMaxPushConstantsSize};
    VkPipelineLayoutCreateInfo plCi{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    plCi.setLayoutCount = 1;
    plCi.pSetLayouts = &sc->descSetLayout;
    plCi.pushConstantRangeCount = 1;
    plCi.pPushConstantRanges = &pcRange;
    if (d->CreatePipelineLayout(d->device, &plCi, nullptr, &sc->pipelineLayout) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreatePipelineLayout falhou");
        return false;
    }

    // --- Shader modules (bytecode embutido em tempo de compilação, ver EmbedSpv.cmake). ---------
    sc->vertModule = LoadShaderModule(d, g_spv_fullscreen, g_spv_fullscreen_size);
    sc->postfxModule = LoadShaderModule(d, g_spv_postfx, g_spv_postfx_size);
    sc->sgsrModule = LoadShaderModule(d, g_spv_sgsr, g_spv_sgsr_size);
    if (!sc->vertModule || !sc->postfxModule || !sc->sgsrModule) return false;

    auto makePipeline = [&](VkShaderModule fragModule) -> VkPipeline {
        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0] = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = sc->vertModule;
        stages[0].pName = "main";
        stages[1] = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = fragModule;
        stages[1].pName = "main";

        VkPipelineVertexInputStateCreateInfo vertexInput{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        // Sem vertex buffers -- o vertex shader gera o triângulo a partir de gl_VertexIndex.

        VkPipelineInputAssemblyStateCreateInfo inputAssembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

        VkViewport viewport{0, 0, (float) sc->extent.width, (float) sc->extent.height, 0.0f, 1.0f};
        VkRect2D scissor{{0, 0}, sc->extent};
        VkPipelineViewportStateCreateInfo viewportState{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewportState.viewportCount = 1;
        viewportState.pViewports = &viewport;
        viewportState.scissorCount = 1;
        viewportState.pScissors = &scissor;

        VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = VK_POLYGON_MODE_FILL;
        raster.cullMode = VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        raster.lineWidth = 1.0f;

        VkPipelineMultisampleStateCreateInfo msaa{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        msaa.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineColorBlendAttachmentState blendAttachment{};
        blendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        blendAttachment.blendEnable = VK_FALSE;
        VkPipelineColorBlendStateCreateInfo blend{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        blend.attachmentCount = 1;
        blend.pAttachments = &blendAttachment;

        // Dynamic state vazio de propósito -- viewport/scissor fixos ao tamanho da swapchain
        // simplifica a primeira versão (recriamos a pipeline inteira se a swapchain mudar de
        // tamanho, o que já acontece via vkCreateSwapchainKHR/oldSwapchain).
        VkGraphicsPipelineCreateInfo pipeCi{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        pipeCi.stageCount = 2;
        pipeCi.pStages = stages;
        pipeCi.pVertexInputState = &vertexInput;
        pipeCi.pInputAssemblyState = &inputAssembly;
        pipeCi.pViewportState = &viewportState;
        pipeCi.pRasterizationState = &raster;
        pipeCi.pMultisampleState = &msaa;
        pipeCi.pColorBlendState = &blend;
        pipeCi.layout = sc->pipelineLayout;
        pipeCi.renderPass = sc->renderPass;
        pipeCi.subpass = 0;

        VkPipeline pipeline = VK_NULL_HANDLE;
        VkResult r = d->CreateGraphicsPipelines(d->device, VK_NULL_HANDLE, 1, &pipeCi, nullptr, &pipeline);
        if (r != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkCreateGraphicsPipelines falhou (%d)", r);
            return VK_NULL_HANDLE;
        }
        return pipeline;
    };

    sc->postfxPipeline = makePipeline(sc->postfxModule);
    sc->sgsrPipeline = makePipeline(sc->sgsrModule);
    if (!sc->postfxPipeline || !sc->sgsrPipeline) return false;

    // --- Command pool (uma vez por swapchain, assume a família de fila do dispositivo). ---------
    VkCommandPoolCreateInfo cpCi{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    cpCi.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    cpCi.queueFamilyIndex = d->queueFamilyIndex;
    if (d->CreateCommandPool(d->device, &cpCi, nullptr, &sc->cmdPool) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkCreateCommandPool falhou");
        return false;
    }

    sc->targetViews.resize(imageCount);
    sc->framebuffers.resize(imageCount);
    sc->scratchImages.resize(imageCount);
    sc->scratchMemories.resize(imageCount);
    sc->scratchViews.resize(imageCount);
    sc->descSets.resize(imageCount);
    sc->cmdBuffers.resize(imageCount);
    sc->fences.resize(imageCount);
    sc->renderDoneSemaphores.resize(imageCount);
    sc->fenceEverSubmitted.assign(imageCount, false);

    VkCommandBufferAllocateInfo cbAlloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cbAlloc.commandPool = sc->cmdPool;
    cbAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbAlloc.commandBufferCount = imageCount;
    if (d->AllocateCommandBuffers(d->device, &cbAlloc, sc->cmdBuffers.data()) != VK_SUCCESS) {
        LOGE("CreateSwapchainResources: vkAllocateCommandBuffers falhou");
        return false;
    }

    for (uint32_t i = 0; i < imageCount; i++) {
        // Vista da imagem real do swapchain, usada como color attachment do nosso render pass.
        VkImageViewCreateInfo viewCi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        viewCi.image = sc->images[i];
        viewCi.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewCi.format = sc->format;
        viewCi.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        if (d->CreateImageView(d->device, &viewCi, nullptr, &sc->targetViews[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkCreateImageView (target) falhou no índice %u", i);
            return false;
        }

        VkFramebufferCreateInfo fbCi{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
        fbCi.renderPass = sc->renderPass;
        fbCi.attachmentCount = 1;
        fbCi.pAttachments = &sc->targetViews[i];
        fbCi.width = sc->extent.width;
        fbCi.height = sc->extent.height;
        fbCi.layers = 1;
        if (d->CreateFramebuffer(d->device, &fbCi, nullptr, &sc->framebuffers[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkCreateFramebuffer falhou no índice %u", i);
            return false;
        }

        // Imagem "scratch": cópia do frame original, amostrável pelo shader (o shader não pode
        // ler e escrever a mesma imagem que está a servir de color attachment ao mesmo tempo).
        VkImageCreateInfo imgCi{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        imgCi.imageType = VK_IMAGE_TYPE_2D;
        imgCi.format = sc->format;
        imgCi.extent = {sc->extent.width, sc->extent.height, 1};
        imgCi.mipLevels = 1;
        imgCi.arrayLayers = 1;
        imgCi.samples = VK_SAMPLE_COUNT_1_BIT;
        imgCi.tiling = VK_IMAGE_TILING_OPTIMAL;
        imgCi.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        imgCi.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (d->CreateImage(d->device, &imgCi, nullptr, &sc->scratchImages[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkCreateImage (scratch) falhou no índice %u", i);
            return false;
        }

        VkMemoryRequirements memReq{};
        d->GetImageMemoryRequirements(d->device, sc->scratchImages[i], &memReq);
        VkMemoryAllocateInfo allocInfo{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocInfo.allocationSize = memReq.size;
        allocInfo.memoryTypeIndex = FindMemoryType(d, memReq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (d->AllocateMemory(d->device, &allocInfo, nullptr, &sc->scratchMemories[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkAllocateMemory (scratch) falhou no índice %u", i);
            return false;
        }
        d->BindImageMemory(d->device, sc->scratchImages[i], sc->scratchMemories[i], 0);

        VkImageViewCreateInfo scratchViewCi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        scratchViewCi.image = sc->scratchImages[i];
        scratchViewCi.viewType = VK_IMAGE_VIEW_TYPE_2D;
        scratchViewCi.format = sc->format;
        scratchViewCi.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        if (d->CreateImageView(d->device, &scratchViewCi, nullptr, &sc->scratchViews[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkCreateImageView (scratch) falhou no índice %u", i);
            return false;
        }

        VkDescriptorSetAllocateInfo dsAlloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        dsAlloc.descriptorPool = sc->descPool;
        dsAlloc.descriptorSetCount = 1;
        dsAlloc.pSetLayouts = &sc->descSetLayout;
        if (d->AllocateDescriptorSets(d->device, &dsAlloc, &sc->descSets[i]) != VK_SUCCESS) {
            LOGE("CreateSwapchainResources: vkAllocateDescriptorSets falhou no índice %u", i);
            return false;
        }

        VkDescriptorImageInfo imgInfo{sc->sampler, sc->scratchViews[i], VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        write.dstSet = sc->descSets[i];
        write.dstBinding = 0;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.pImageInfo = &imgInfo;
        d->UpdateDescriptorSets(d->device, 1, &write, 0, nullptr);

        VkFenceCreateInfo fenceCi{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        fenceCi.flags = VK_FENCE_CREATE_SIGNALED_BIT; // já "sinalizada" -- a 1ª espera não bloqueia
        d->CreateFence(d->device, &fenceCi, nullptr, &sc->fences[i]);

        VkSemaphoreCreateInfo semCi{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        d->CreateSemaphore(d->device, &semCi, nullptr, &sc->renderDoneSemaphores[i]);
    }

    sc->ready = true;
    LOGI("CreateSwapchainResources: OK (%u imagens, %ux%u, formato=%d)", imageCount, sc->extent.width, sc->extent.height, sc->format);
    return true;
}

// -------------------------------------------------------------------------------------------
// vkQueuePresentKHR: aqui é onde o pass de pós-processamento realmente acontece.
// -------------------------------------------------------------------------------------------

void image_barrier(DeviceData *d, VkCommandBuffer cmd, VkImage image, VkImageLayout oldLayout,
                    VkImageLayout newLayout, VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                    VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage) {
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    barrier.srcAccessMask = srcAccess;
    barrier.dstAccessMask = dstAccess;
    d->CmdPipelineBarrier(cmd, srcStage, dstStage, 0, 0, nullptr, 0, nullptr, 1, &barrier);
}

// Processa UM swapchain de um pedido de present. Devolve false só em erro grave (nesse caso o
// chamador ainda deve presentear o frame original, sem efeito, em vez de perder o frame do jogo.
bool ProcessSwapchainPresent(DeviceData *d, VkQueue queue, SwapchainData *sc, uint32_t imageIndex,
                              uint32_t waitSemaphoreCount, const VkSemaphore *pWaitSemaphores) {
    if (!sc->ready) return false;
    if (g_isOwnProcess) return false; // nunca aplicar efeitos à própria Odin Hub -- ver DetectIsOwnProcess()

    const odin::LayerConfig &cfg = g_config.get();
    bool useSgsr = cfg.sgsrEnabled;
    bool useEffect = useSgsr || cfg.reshadeEffectId != 0;
    if (!useEffect) return false; // nada a fazer -- o chamador presenteia o frame original

    d->WaitForFences(d->device, 1, &sc->fences[imageIndex], VK_TRUE, UINT64_MAX);
    d->ResetFences(d->device, 1, &sc->fences[imageIndex]);

    VkCommandBuffer cmd = sc->cmdBuffers[imageIndex];
    d->ResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo beginInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    d->BeginCommandBuffer(cmd, &beginInfo);

    VkImage swapImage = sc->images[imageIndex];
    VkImage scratch = sc->scratchImages[imageIndex];

    // 1) swapchain image: PRESENT_SRC_KHR -> TRANSFER_SRC_OPTIMAL (vamos ler o frame já pronto)
    image_barrier(d, cmd, swapImage, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                  VK_ACCESS_MEMORY_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                  VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    // 2) scratch: UNDEFINED -> TRANSFER_DST_OPTIMAL (não nos importa o conteúdo anterior, vamos
    //    sobrescrevê-lo por completo já a seguir).
    image_barrier(d, cmd, scratch, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                  0, VK_ACCESS_TRANSFER_WRITE_BIT,
                  VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    VkImageCopy copyRegion{};
    copyRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copyRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copyRegion.extent = {sc->extent.width, sc->extent.height, 1};
    d->CmdCopyImage(cmd, swapImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, scratch,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copyRegion);

    // 3) scratch: TRANSFER_DST_OPTIMAL -> SHADER_READ_ONLY_OPTIMAL (para o shader poder amostrar)
    image_barrier(d, cmd, scratch, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                  VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

    // 4) swapchain image: TRANSFER_SRC_OPTIMAL -> COLOR_ATTACHMENT_OPTIMAL (vamos desenhar nela)
    image_barrier(d, cmd, swapImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                  VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                  VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

    VkRenderPassBeginInfo rpBegin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    rpBegin.renderPass = sc->renderPass;
    rpBegin.framebuffer = sc->framebuffers[imageIndex];
    rpBegin.renderArea = {{0, 0}, sc->extent};
    d->CmdBeginRenderPass(cmd, &rpBegin, VK_SUBPASS_CONTENTS_INLINE);

    d->CmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, useSgsr ? sc->sgsrPipeline : sc->postfxPipeline);
    d->CmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, sc->pipelineLayout, 0, 1,
                              &sc->descSets[imageIndex], 0, nullptr);

    if (useSgsr) {
        SgsrPushConstants pc{};
        pc.useTexAlpha = 0;
        pc.invSrcW = 1.0f / (float) sc->extent.width;
        pc.invSrcH = 1.0f / (float) sc->extent.height;
        pc.srcW = (float) sc->extent.width;
        pc.srcH = (float) sc->extent.height;
        pc.effectId = cfg.sgsrMode;
        pc.resW = (float) sc->extent.width;
        pc.sharpness = cfg.sgsrSharpness;
        d->CmdPushConstants(cmd, sc->pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
    } else {
        PostfxPushConstants pc{};
        pc.effectId = cfg.reshadeEffectId;
        // O "ReShade" ainda não tem o seu próprio slider de intensidade na UI (ver AUDIT) -- 0.6
        // é um meio-termo razoável para todos os efeitos curados; fica fácil de ligar a um slider
        // real mais tarde (a maior parte dos efeitos em window_postfx.frag já usa `sharpness`
        // como o seu parâmetro de intensidade 0..1 genérico).
        pc.sharpness = 0.6f;
        pc.resW = (float) sc->extent.width;
        pc.resH = (float) sc->extent.height;
        d->CmdPushConstants(cmd, sc->pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
    }

    d->CmdDraw(cmd, 3, 1, 0, 0);
    d->CmdEndRenderPass(cmd);

    // 5) swapchain image: COLOR_ATTACHMENT_OPTIMAL -> PRESENT_SRC_KHR (pronta a apresentar)
    image_barrier(d, cmd, swapImage, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                  VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_MEMORY_READ_BIT,
                  VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);

    d->EndCommandBuffer(cmd);

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.waitSemaphoreCount = waitSemaphoreCount;
    submit.pWaitSemaphores = pWaitSemaphores;
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &cmd;
    submit.signalSemaphoreCount = 1;
    submit.pSignalSemaphores = &sc->renderDoneSemaphores[imageIndex];

    VkResult r = d->QueueSubmit(queue, 1, &submit, sc->fences[imageIndex]);
    if (r != VK_SUCCESS) {
        LOGE("ProcessSwapchainPresent: vkQueueSubmit falhou (%d)", r);
        return false;
    }
    sc->fenceEverSubmitted[imageIndex] = true;
    return true;
}

VkResult Odin_vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR *pPresentInfo) {
    DeviceData *d = FindDeviceData(queue);
    if (!d) return VK_ERROR_DEVICE_LOST; // não deveria acontecer -- a fila vem sempre de um device conhecido

    // Caminho normal: um único swapchain por chamada (o caso comum em jogos Android). Para cada
    // swapchain, decidimos individualmente se corremos o nosso pass ou presenteamos sem alteração.
    bool anyProcessed = false;
    std::vector<VkSemaphore> ourWaitSem;
    std::vector<VkSwapchainKHR> passthroughSwapchains;
    std::vector<uint32_t> passthroughIndices;
    std::vector<VkSwapchainKHR> processedSwapchains;
    std::vector<uint32_t> processedIndices;
    std::vector<VkSemaphore> processedSignalSem;

    for (uint32_t i = 0; i < pPresentInfo->swapchainCount; i++) {
        VkSwapchainKHR swapchain = pPresentInfo->pSwapchains[i];
        SwapchainData *sc = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto it = d->swapchains.find(swapchain);
            if (it != d->swapchains.end()) sc = it->second.get();
        }
        bool processed = sc && ProcessSwapchainPresent(d, queue, sc, pPresentInfo->pImageIndices[i],
                                                         pPresentInfo->waitSemaphoreCount,
                                                         pPresentInfo->pWaitSemaphores);
        if (processed) {
            anyProcessed = true;
            processedSwapchains.push_back(swapchain);
            processedIndices.push_back(pPresentInfo->pImageIndices[i]);
            processedSignalSem.push_back(sc->renderDoneSemaphores[pPresentInfo->pImageIndices[i]]);
        } else {
            passthroughSwapchains.push_back(swapchain);
            passthroughIndices.push_back(pPresentInfo->pImageIndices[i]);
        }
    }

    if (!anyProcessed) {
        // Nada foi processado (efeitos desligados, ou erro) -- presenteia tudo exactamente como
        // o jogo pediu. Este é o caminho "quente" quando SGSR/ReShade estão desligados: zero
        // overhead extra além de uma consulta às propriedades (cacheada, ver OdinLayerConfig.h).
        return d->QueuePresentKHR(queue, pPresentInfo);
    }

    // Pelo menos um swapchain foi processado por nós: presenteia os processados a events de num
    // present call próprio (à espera do NOSSO semáforo em vez do da app), e os restantes (se
    // houver, caso raro de multi-swapchain com um deles sem recursos prontos) num segundo.
    VkResult finalResult = VK_SUCCESS;
    if (!processedSwapchains.empty()) {
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        present.waitSemaphoreCount = (uint32_t) processedSignalSem.size();
        present.pWaitSemaphores = processedSignalSem.data();
        present.swapchainCount = (uint32_t) processedSwapchains.size();
        present.pSwapchains = processedSwapchains.data();
        present.pImageIndices = processedIndices.data();
        finalResult = d->QueuePresentKHR(queue, &present);
    }
    if (!passthroughSwapchains.empty()) {
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        present.waitSemaphoreCount = pPresentInfo->waitSemaphoreCount;
        present.pWaitSemaphores = pPresentInfo->pWaitSemaphores;
        present.swapchainCount = (uint32_t) passthroughSwapchains.size();
        present.pSwapchains = passthroughSwapchains.data();
        present.pImageIndices = passthroughIndices.data();
        VkResult r2 = d->QueuePresentKHR(queue, &present);
        if (finalResult == VK_SUCCESS) finalResult = r2;
    }
    return finalResult;
}

// -------------------------------------------------------------------------------------------
// vkCreateSwapchainKHR / vkDestroySwapchainKHR
// -------------------------------------------------------------------------------------------

VkResult Odin_vkCreateSwapchainKHR(VkDevice device, const VkSwapchainCreateInfoKHR *pCreateInfo,
                                    const VkAllocationCallbacks *pAllocator, VkSwapchainKHR *pSwapchain) {
    DeviceData *d = FindDeviceData(device);
    if (!d) return VK_ERROR_DEVICE_LOST;

    VkResult result = d->CreateSwapchainKHR(device, pCreateInfo, pAllocator, pSwapchain);
    if (result != VK_SUCCESS) return result;

    auto sc = std::make_unique<SwapchainData>();
    sc->swapchain = *pSwapchain;
    sc->format = pCreateInfo->imageFormat;
    sc->extent = pCreateInfo->imageExtent;

    uint32_t imageCount = 0;
    d->GetSwapchainImagesKHR(device, *pSwapchain, &imageCount, nullptr);
    sc->images.resize(imageCount);
    d->GetSwapchainImagesKHR(device, *pSwapchain, &imageCount, sc->images.data());

    bool ok = CreateSwapchainResources(d, sc.get());
    if (!ok) {
        LOGW("Odin_vkCreateSwapchainKHR: falha a preparar recursos -- SGSR/ReShade ficam "
             "desligados nesta swapchain, mas o jogo continua a correr normalmente.");
        DestroySwapchainResources(d, sc.get());
        sc->ready = false;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    d->swapchains[*pSwapchain] = std::move(sc);
    return VK_SUCCESS;
}

void Odin_vkDestroySwapchainKHR(VkDevice device, VkSwapchainKHR swapchain, const VkAllocationCallbacks *pAllocator) {
    DeviceData *d = FindDeviceData(device);
    if (d) {
        std::unique_ptr<SwapchainData> sc;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto it = d->swapchains.find(swapchain);
            if (it != d->swapchains.end()) {
                sc = std::move(it->second);
                d->swapchains.erase(it);
            }
        }
        if (sc) {
            d->DeviceWaitIdle(device); // garante que nada nosso ainda usa estes recursos
            DestroySwapchainResources(d, sc.get());
        }
        d->DestroySwapchainKHR(device, swapchain, pAllocator);
    }
}

// -------------------------------------------------------------------------------------------
// vkCreateInstance / vkDestroyInstance
// -------------------------------------------------------------------------------------------

VkResult Odin_vkCreateInstance(const VkInstanceCreateInfo *pCreateInfo, const VkAllocationCallbacks *pAllocator,
                                VkInstance *pInstance) {
    // Percorre a pNext chain à procura do VK_LAYER_LINK_INFO desta camada (padrão de todas as
    // camadas Vulkan -- ver docs/LoaderLayerInterface.md do Vulkan-Loader).
    auto *layerCreateInfo = (VkLayerInstanceCreateInfo *) pCreateInfo->pNext;
    while (layerCreateInfo && !(layerCreateInfo->sType == VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO &&
                                 layerCreateInfo->function == VK_LAYER_LINK_INFO)) {
        layerCreateInfo = (VkLayerInstanceCreateInfo *) layerCreateInfo->pNext;
    }
    if (!layerCreateInfo) {
        LOGE("Odin_vkCreateInstance: VK_LAYER_LINK_INFO não encontrado na pNext chain");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    PFN_vkGetInstanceProcAddr gipa = layerCreateInfo->u.pLayerInfo->pfnNextGetInstanceProcAddr;
    // Avança o ponteiro para a próxima camada da cadeia ANTES de chamar para baixo -- é assim que
    // cada camada "se remove a si própria" da perspectiva de quem vier depois na cadeia.
    layerCreateInfo->u.pLayerInfo = layerCreateInfo->u.pLayerInfo->pNext;

    auto createInstance = (PFN_vkCreateInstance) gipa(nullptr, "vkCreateInstance");
    VkResult result = createInstance(pCreateInfo, pAllocator, pInstance);
    if (result != VK_SUCCESS) return result;

    auto data = std::make_unique<InstanceData>();
    data->instance = *pInstance;
    data->gipa = gipa;
    data->DestroyInstance = (PFN_vkDestroyInstance) gipa(*pInstance, "vkDestroyInstance");
    data->GetPhysicalDeviceMemoryProperties =
            (PFN_vkGetPhysicalDeviceMemoryProperties) gipa(*pInstance, "vkGetPhysicalDeviceMemoryProperties");

    std::lock_guard<std::mutex> lock(g_mutex);
    g_instances[ODIN_DISPATCH_KEY(*pInstance)] = std::move(data);
    LOGI("Odin_vkCreateInstance: camada inicializada para uma nova VkInstance");
    return VK_SUCCESS;
}

void Odin_vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks *pAllocator) {
    InstanceData *data = FindInstanceData(instance);
    if (data) {
        PFN_vkDestroyInstance destroy = data->DestroyInstance;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            g_instances.erase(ODIN_DISPATCH_KEY(instance));
        }
        destroy(instance, pAllocator);
    }
}

// -------------------------------------------------------------------------------------------
// vkCreateDevice / vkDestroyDevice
// -------------------------------------------------------------------------------------------

VkResult Odin_vkCreateDevice(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo *pCreateInfo,
                              const VkAllocationCallbacks *pAllocator, VkDevice *pDevice) {
    auto *layerCreateInfo = (VkLayerDeviceCreateInfo *) pCreateInfo->pNext;
    while (layerCreateInfo && !(layerCreateInfo->sType == VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO &&
                                 layerCreateInfo->function == VK_LAYER_LINK_INFO)) {
        layerCreateInfo = (VkLayerDeviceCreateInfo *) layerCreateInfo->pNext;
    }
    if (!layerCreateInfo) {
        LOGE("Odin_vkCreateDevice: VK_LAYER_LINK_INFO não encontrado na pNext chain");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    PFN_vkGetInstanceProcAddr gipa = layerCreateInfo->u.pLayerInfo->pfnNextGetInstanceProcAddr;
    PFN_vkGetDeviceProcAddr gdpa = layerCreateInfo->u.pLayerInfo->pfnNextGetDeviceProcAddr;
    layerCreateInfo->u.pLayerInfo = layerCreateInfo->u.pLayerInfo->pNext;

    auto createDevice = (PFN_vkCreateDevice) gipa(nullptr, "vkCreateDevice");
    VkResult result = createDevice(physicalDevice, pCreateInfo, pAllocator, pDevice);
    if (result != VK_SUCCESS) return result;

    VkDevice device = *pDevice;
    auto data = std::make_unique<DeviceData>();
    data->device = device;
    data->physicalDevice = physicalDevice;
    data->gdpa = gdpa;
    data->queueFamilyIndex = pCreateInfo->queueCreateInfoCount > 0
                                     ? pCreateInfo->pQueueCreateInfos[0].queueFamilyIndex
                                     : 0; // ver nota de simplificação #3 no topo do ficheiro

    // Instância dona deste physical device -- physical devices partilham a dispatch key da sua
    // VkInstance (ver ODIN_DISPATCH_KEY em OdinVkLayerCompat.h).
    InstanceData *inst = FindInstanceData(physicalDevice);

#define ODIN_LOAD(field, name) data->field = (decltype(data->field)) gdpa(device, name)
    ODIN_LOAD(DestroyDevice, "vkDestroyDevice");
    ODIN_LOAD(CreateSwapchainKHR, "vkCreateSwapchainKHR");
    ODIN_LOAD(DestroySwapchainKHR, "vkDestroySwapchainKHR");
    ODIN_LOAD(GetSwapchainImagesKHR, "vkGetSwapchainImagesKHR");
    ODIN_LOAD(QueuePresentKHR, "vkQueuePresentKHR");
    ODIN_LOAD(CreateImage, "vkCreateImage");
    ODIN_LOAD(DestroyImage, "vkDestroyImage");
    ODIN_LOAD(AllocateMemory, "vkAllocateMemory");
    ODIN_LOAD(FreeMemory, "vkFreeMemory");
    ODIN_LOAD(BindImageMemory, "vkBindImageMemory");
    ODIN_LOAD(GetImageMemoryRequirements, "vkGetImageMemoryRequirements");
    ODIN_LOAD(CreateImageView, "vkCreateImageView");
    ODIN_LOAD(DestroyImageView, "vkDestroyImageView");
    ODIN_LOAD(CreateRenderPass, "vkCreateRenderPass");
    ODIN_LOAD(DestroyRenderPass, "vkDestroyRenderPass");
    ODIN_LOAD(CreateFramebuffer, "vkCreateFramebuffer");
    ODIN_LOAD(DestroyFramebuffer, "vkDestroyFramebuffer");
    ODIN_LOAD(CreateShaderModule, "vkCreateShaderModule");
    ODIN_LOAD(DestroyShaderModule, "vkDestroyShaderModule");
    ODIN_LOAD(CreatePipelineLayout, "vkCreatePipelineLayout");
    ODIN_LOAD(DestroyPipelineLayout, "vkDestroyPipelineLayout");
    ODIN_LOAD(CreateGraphicsPipelines, "vkCreateGraphicsPipelines");
    ODIN_LOAD(DestroyPipeline, "vkDestroyPipeline");
    ODIN_LOAD(CreateDescriptorSetLayout, "vkCreateDescriptorSetLayout");
    ODIN_LOAD(DestroyDescriptorSetLayout, "vkDestroyDescriptorSetLayout");
    ODIN_LOAD(CreateDescriptorPool, "vkCreateDescriptorPool");
    ODIN_LOAD(DestroyDescriptorPool, "vkDestroyDescriptorPool");
    ODIN_LOAD(AllocateDescriptorSets, "vkAllocateDescriptorSets");
    ODIN_LOAD(UpdateDescriptorSets, "vkUpdateDescriptorSets");
    ODIN_LOAD(CreateSampler, "vkCreateSampler");
    ODIN_LOAD(DestroySampler, "vkDestroySampler");
    ODIN_LOAD(CreateCommandPool, "vkCreateCommandPool");
    ODIN_LOAD(DestroyCommandPool, "vkDestroyCommandPool");
    ODIN_LOAD(AllocateCommandBuffers, "vkAllocateCommandBuffers");
    ODIN_LOAD(FreeCommandBuffers, "vkFreeCommandBuffers");
    ODIN_LOAD(ResetCommandBuffer, "vkResetCommandBuffer");
    ODIN_LOAD(BeginCommandBuffer, "vkBeginCommandBuffer");
    ODIN_LOAD(EndCommandBuffer, "vkEndCommandBuffer");
    ODIN_LOAD(CmdPipelineBarrier, "vkCmdPipelineBarrier");
    ODIN_LOAD(CmdCopyImage, "vkCmdCopyImage");
    ODIN_LOAD(CmdBeginRenderPass, "vkCmdBeginRenderPass");
    ODIN_LOAD(CmdEndRenderPass, "vkCmdEndRenderPass");
    ODIN_LOAD(CmdBindPipeline, "vkCmdBindPipeline");
    ODIN_LOAD(CmdBindDescriptorSets, "vkCmdBindDescriptorSets");
    ODIN_LOAD(CmdPushConstants, "vkCmdPushConstants");
    ODIN_LOAD(CmdSetViewport, "vkCmdSetViewport");
    ODIN_LOAD(CmdSetScissor, "vkCmdSetScissor");
    ODIN_LOAD(CmdDraw, "vkCmdDraw");
    ODIN_LOAD(CreateFence, "vkCreateFence");
    ODIN_LOAD(DestroyFence, "vkDestroyFence");
    ODIN_LOAD(WaitForFences, "vkWaitForFences");
    ODIN_LOAD(ResetFences, "vkResetFences");
    ODIN_LOAD(CreateSemaphore, "vkCreateSemaphore");
    ODIN_LOAD(DestroySemaphore, "vkDestroySemaphore");
    ODIN_LOAD(QueueSubmit, "vkQueueSubmit");
    ODIN_LOAD(DeviceWaitIdle, "vkDeviceWaitIdle");
#undef ODIN_LOAD

    if (inst) {
        data->GetPhysicalDeviceMemoryProperties = inst->GetPhysicalDeviceMemoryProperties;
    } else {
        LOGW("Odin_vkCreateDevice: InstanceData não encontrada para este VkPhysicalDevice -- "
             "SGSR/ReShade podem não conseguir alocar memória correctamente.");
    }

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_devices[ODIN_DISPATCH_KEY(device)] = std::move(data);
    }
    LOGI("Odin_vkCreateDevice: camada inicializada para um novo VkDevice (fila assumida=%u)",
         pCreateInfo->queueCreateInfoCount > 0 ? pCreateInfo->pQueueCreateInfos[0].queueFamilyIndex : 0);
    return VK_SUCCESS;
}

void Odin_vkDestroyDevice(VkDevice device, const VkAllocationCallbacks *pAllocator) {
    std::unique_ptr<DeviceData> data;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_devices.find(ODIN_DISPATCH_KEY(device));
        if (it != g_devices.end()) {
            data = std::move(it->second);
            g_devices.erase(it);
        }
    }
    if (data) {
        for (auto &kv : data->swapchains) DestroySwapchainResources(data.get(), kv.second.get());
        data->DestroyDevice(device, pAllocator);
    }
}

// -------------------------------------------------------------------------------------------
// Despacho de vkGetInstanceProcAddr / vkGetDeviceProcAddr -- devolve as NOSSAS funções para as
// que intercetamos, e delega tudo o resto para a próxima camada da cadeia.
// -------------------------------------------------------------------------------------------

PFN_vkVoidFunction Odin_InterceptedInstanceProc(const char *name) {
    if (!strcmp(name, "vkCreateInstance")) return (PFN_vkVoidFunction) Odin_vkCreateInstance;
    if (!strcmp(name, "vkDestroyInstance")) return (PFN_vkVoidFunction) Odin_vkDestroyInstance;
    if (!strcmp(name, "vkCreateDevice")) return (PFN_vkVoidFunction) Odin_vkCreateDevice;
    return nullptr;
}

PFN_vkVoidFunction Odin_InterceptedDeviceProc(const char *name) {
    if (!strcmp(name, "vkDestroyDevice")) return (PFN_vkVoidFunction) Odin_vkDestroyDevice;
    if (!strcmp(name, "vkCreateSwapchainKHR")) return (PFN_vkVoidFunction) Odin_vkCreateSwapchainKHR;
    if (!strcmp(name, "vkDestroySwapchainKHR")) return (PFN_vkVoidFunction) Odin_vkDestroySwapchainKHR;
    if (!strcmp(name, "vkQueuePresentKHR")) return (PFN_vkVoidFunction) Odin_vkQueuePresentKHR;
    return nullptr;
}

} // namespace

VK_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL
vkNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface *pVersionStruct) {
    if (pVersionStruct->loaderLayerInterfaceVersion > ODIN_CURRENT_LOADER_LAYER_INTERFACE_VERSION) {
        pVersionStruct->loaderLayerInterfaceVersion = ODIN_CURRENT_LOADER_LAYER_INTERFACE_VERSION;
    }
    pVersionStruct->pfnGetInstanceProcAddr = nullptr; // preenchidos abaixo via vkGetInstanceProcAddr
    pVersionStruct->pfnGetDeviceProcAddr = nullptr;
    pVersionStruct->pfnGetPhysicalDeviceProcAddr = nullptr;
    return VK_SUCCESS;
}

VK_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetDeviceProcAddr(VkDevice device, const char *pName) {
    PFN_vkVoidFunction intercepted = Odin_InterceptedDeviceProc(pName);
    if (intercepted) return intercepted;
    DeviceData *d = FindDeviceData(device);
    return d && d->gdpa ? d->gdpa(device, pName) : nullptr;
}

VK_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetInstanceProcAddr(VkInstance instance, const char *pName) {
    PFN_vkVoidFunction intercepted = Odin_InterceptedInstanceProc(pName);
    if (intercepted) return intercepted;
    // vkGetDeviceProcAddr também tem de poder ser pedido através da instância (parte do
    // contrato do Vulkan) -- devolvemos a nossa própria para continuar a intercetar por-device.
    if (!strcmp(pName, "vkGetDeviceProcAddr")) return (PFN_vkVoidFunction) vkGetDeviceProcAddr;
    if (!instance) return nullptr;
    InstanceData *data = FindInstanceData(instance);
    return data && data->gipa ? data->gipa(instance, pName) : nullptr;
}

// A .so é carregada pelo loader Vulkan do SISTEMA via dlopen (mecanismo `debug.vulkan.layer.dir`),
// nunca via JNI/System.loadLibrary -- por isso não há (nem faz sentido haver) um JNI_OnLoad aqui.
// Este construtor corre assim que o dlopen termina, e é o primeiro sinal no logcat de que a
// camada pelo menos chegou a ser carregada no processo do jogo (antes mesmo de qualquer
// VkInstance ser criada) -- útil para a primeira validação no dispositivo: se isto nunca aparecer
// no logcat do processo do jogo, o problema é de DESCOBERTA da camada (nome/caminho errados em
// `debug.vulkan.layers`/`debug.vulkan.layer.dir`), não da lógica de interceção em si.
__attribute__((constructor)) static void OdinLayerOnLoad() {
    LOGI("VkLayer_OdinHub carregada no processo (pid=%d) -- pronta a interceptar vkQueuePresentKHR. own_process=%d",
         getpid(), (int) g_isOwnProcess);
}
