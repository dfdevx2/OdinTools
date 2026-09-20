#pragma once

// Estruturas mínimas da "interface loader<->layer" do Vulkan, copiadas à mão a partir do
// vk_layer.h oficial (KhronosGroup/Vulkan-Headers). O NDK do Android nem sempre inclui este
// ficheiro (ao contrário de vulkan.h/vulkan_core.h, que vêm sempre) -- por isso declaramos aqui só
// o que precisamos, em vez de depender de um #include que pode não existir no ambiente de build.
// Os nomes e layouts têm de bater exactamente certo com os do loader (é assim que ele entende a
// pNext chain ao criar a instância/dispositivo), por isso não são inventados: foram confirmados
// contra o cabeçalho oficial antes de escrever este ficheiro.
//
// Precisa de <vulkan/vulkan.h> incluído ANTES deste ficheiro (para VkStructureType,
// PFN_vkGetInstanceProcAddr, PFN_vkGetDeviceProcAddr, etc.).

typedef enum VkLayerFunction_ {
    VK_LAYER_LINK_INFO = 0,
    VK_LOADER_DATA_CALLBACK = 1,
    VK_LOADER_LAYER_CREATE_DEVICE_CALLBACK = 2,
    VK_LOADER_FEATURES = 3,
} VkLayerFunction;

typedef PFN_vkVoidFunction (VKAPI_PTR *PFN_GetPhysicalDeviceProcAddr)(VkInstance instance, const char *pName);

typedef struct VkLayerInstanceLink_ {
    struct VkLayerInstanceLink_ *pNext;
    PFN_vkGetInstanceProcAddr pfnNextGetInstanceProcAddr;
    PFN_GetPhysicalDeviceProcAddr pfnNextGetPhysicalDeviceProcAddr;
} VkLayerInstanceLink;

typedef struct VkLayerDeviceLink_ {
    struct VkLayerDeviceLink_ *pNext;
    PFN_vkGetInstanceProcAddr pfnNextGetInstanceProcAddr;
    PFN_vkGetDeviceProcAddr pfnNextGetDeviceProcAddr;
} VkLayerDeviceLink;

typedef VkResult (VKAPI_PTR *PFN_vkSetInstanceLoaderData)(VkInstance instance, void *object);
typedef VkResult (VKAPI_PTR *PFN_vkSetDeviceLoaderData)(VkDevice device, void *object);

typedef struct {
    VkStructureType sType; // VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO
    const void *pNext;
    VkLayerFunction function;
    union {
        VkLayerInstanceLink *pLayerInfo;
        PFN_vkSetInstanceLoaderData pfnSetInstanceLoaderData;
        struct {
            void *pfnLayerCreateDevice;
            void *pfnLayerDestroyDevice;
        } layerDevice;
        uint64_t loaderFeatures;
    } u;
} VkLayerInstanceCreateInfo;

typedef struct {
    VkStructureType sType; // VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO
    const void *pNext;
    VkLayerFunction function;
    union {
        VkLayerDeviceLink *pLayerInfo;
        PFN_vkSetDeviceLoaderData pfnSetDeviceLoaderData;
    } u;
} VkLayerDeviceCreateInfo;

typedef enum VkNegotiateLayerStructType {
    LAYER_NEGOTIATE_UNINTIALIZED = 0,
    LAYER_NEGOTIATE_INTERFACE_STRUCT = 1,
} VkNegotiateLayerStructType;

typedef struct VkNegotiateLayerInterface {
    VkNegotiateLayerStructType sType;
    void *pNext;
    uint32_t loaderLayerInterfaceVersion;
    PFN_vkGetInstanceProcAddr pfnGetInstanceProcAddr;
    PFN_vkGetDeviceProcAddr pfnGetDeviceProcAddr;
    PFN_GetPhysicalDeviceProcAddr pfnGetPhysicalDeviceProcAddr;
} VkNegotiateLayerInterface;

#define ODIN_CURRENT_LOADER_LAYER_INTERFACE_VERSION 2
#define ODIN_MIN_SUPPORTED_LOADER_LAYER_INTERFACE_VERSION 1

// GET_DISPATCH_KEY: todo o objeto "dispatchable" do Vulkan (VkInstance, VkPhysicalDevice,
// VkDevice, VkQueue, VkCommandBuffer) tem, como primeiro campo binário, um ponteiro para a tabela
// de dispatch do loader -- igual em todos os objetos criados a partir da mesma instância/
// dispositivo. É esse ponteiro (não o valor do handle em si) que usamos como chave para encontrar
// "qual dispositivo/instância é este objeto", independentemente de qual camada o está a ver.
#define ODIN_DISPATCH_KEY(handle) (*reinterpret_cast<void **>(handle))
