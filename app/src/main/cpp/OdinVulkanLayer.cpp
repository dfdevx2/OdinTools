#include <vulkan/vulkan.h>
#include <android/log.h>
#include <jni.h>
#include <cstring>

#define LOG_TAG "OdinHubLayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Variáveis de estado global (serão lidas pelos shaders do Ludashi)
bool g_sgsrEnabled = false;
int g_sgsrMode = 0; // 0=Quality, 1=Balanced, 2=Performance, 3=Ultra

bool g_lsfgEnabled = false;
int g_lsfgMultiplier = 2;
bool g_lsfgFramePacing = true;

int g_reshadeProfileId = 0; // 0=Native, 1=Vibrant, etc.
float g_saturation = 1.0f;
float g_temperature = 6500.0f;

extern "C" {

// --- PONTE DE COMUNICAÇÃO: RECEBE DADOS DO KOTLIN ---

JNIEXPORT void JNICALL
Java_de_langerhans_odintools_tools_hardware_VulkanNativeBridge_updateSgsrSettings(
        JNIEnv* env, jobject thiz, jboolean enabled, jint mode) {
    g_sgsrEnabled = enabled;
    g_sgsrMode = mode;
    LOGI("C++ Recebeu SGSR: Ligado=%d, Modo=%d", enabled, mode);
}

JNIEXPORT void JNICALL
Java_de_langerhans_odintools_tools_hardware_VulkanNativeBridge_updateLsfgSettings(
        JNIEnv* env, jobject thiz, jboolean enabled, jint multiplier, jboolean framePacing) {
    g_lsfgEnabled = enabled;
    g_lsfgMultiplier = multiplier;
    g_lsfgFramePacing = framePacing;
    LOGI("C++ Recebeu LSFG: Ligado=%d, Multiplicador=%dx, Pacing=%d", enabled, multiplier, framePacing);
}

JNIEXPORT void JNICALL
Java_de_langerhans_odintools_tools_hardware_VulkanNativeBridge_updateReshadeSettings(
        JNIEnv* env, jobject thiz, jint profileId, jfloat saturation, jfloat temperature) {
    g_reshadeProfileId = profileId;
    g_saturation = saturation;
    g_temperature = temperature;
    LOGI("C++ Recebeu ReShade: Perfil=%d, Saturação=%.2f, Temp=%.0f", profileId, saturation, temperature);
}

// --- GANCHOS VULKAN (INTERCEPTAÇÃO) ---

VKAPI_ATTR VkResult VKAPI_CALL Odin_vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo) {
    // Aqui a mágica vai acontecer: leremos g_sgsrEnabled e aplicaremos o shader antes de despachar o frame
    return VK_SUCCESS;
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL GetDeviceProcAddr(VkDevice device, const char* pName) {
    if (strcmp(pName, "vkQueuePresentKHR") == 0) {
        return reinterpret_cast<PFN_vkVoidFunction>(Odin_vkQueuePresentKHR);
    }
    return nullptr;
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL GetInstanceProcAddr(VkInstance instance, const char* pName) {
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) {
        return reinterpret_cast<PFN_vkVoidFunction>(GetDeviceProcAddr);
    }
    return nullptr;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Odin Hub Vulkan Layer Inicializada!");
    return JNI_VERSION_1_6;
}

} // extern "C"