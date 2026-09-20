#pragma once

#include <sys/system_properties.h>
#include <cstdlib>
#include <cstring>
#include <atomic>
#include <chrono>

// CAUSA RAIZ que corrigimos aqui, antes de escrever qualquer linha da própria camada Vulkan:
//
// A versão anterior (VulkanNativeBridge.kt + OdinVulkanLayer.cpp) comunicava configuração via JNI
// para variáveis globais C++ -- mas essas variáveis vivem na memória do PROCESSO da app Odin Hub.
// A camada Vulkan que precisa de facto de ler "SGSR está ligado? em que modo?" corre dentro do
// PROCESSO DO JOGO (é lá que o `libVkLayer_OdinHub.so` é carregado pelo loader Vulkan do sistema,
// nunca no nosso próprio processo) -- processos distintos, memória distinta. Um JNI setter no
// nosso processo nunca chegaria a influenciar a instância da camada a correr dentro do jogo. Por
// isso a "ponte nativa" existia mas não fazia absolutamente nada de útil.
//
// Corrigido usando **propriedades de sistema Android** (`__system_property_get`/`setprop`) como
// canal de configuração entre processos -- é leve, não precisa de sockets/ficheiros/permissões
// especiais, e o resto da app já usa `setprop` extensivamente via root (ver ShellExecutor). O lado
// Kotlin (ver GraphicsLayerManager.kt) escreve estas propriedades via root quando o utilizador
// muda uma definição; a camada, a correr dentro do jogo, lê-as -- com um pequeno cache/TTL para
// não pagar o custo de uma syscall a cada frame.
namespace odin {

struct LayerConfig {
    bool sgsrEnabled = false;
    int sgsrMode = 0;         // 0=Quality,1=Balanced,2=Performance,3=Ultra
    float sgsrSharpness = 0.5f;

    int reshadeEffectId = 0;  // ver EFFECT_ID_* / window_postfx.frag
    float saturation = 1.0f;
    float temperature = 6500.0f;

    bool lsfgEnabled = false;
    int lsfgMultiplier = 2;
};

namespace detail {
inline bool propBool(const char *key, bool def) {
    char buf[PROP_VALUE_MAX] = {0};
    if (__system_property_get(key, buf) <= 0) return def;
    return buf[0] == '1';
}
inline int propInt(const char *key, int def) {
    char buf[PROP_VALUE_MAX] = {0};
    if (__system_property_get(key, buf) <= 0) return def;
    return atoi(buf);
}
inline float propFloat(const char *key, float def) {
    char buf[PROP_VALUE_MAX] = {0};
    if (__system_property_get(key, buf) <= 0) return def;
    return static_cast<float>(atof(buf));
}
} // namespace detail

// Prefixo comum -- ver GraphicsLayerManager.kt, tem de bater certo com o que é escrito lá.
constexpr const char *PROP_SGSR_ENABLED = "debug.odinhub.sgsr.on";
constexpr const char *PROP_SGSR_MODE = "debug.odinhub.sgsr.mode";
constexpr const char *PROP_SGSR_SHARP = "debug.odinhub.sgsr.sharp";
constexpr const char *PROP_RESHADE_EFFECT = "debug.odinhub.fx.effect";
constexpr const char *PROP_RESHADE_SAT = "debug.odinhub.fx.sat";
constexpr const char *PROP_RESHADE_TEMP = "debug.odinhub.fx.temp";
constexpr const char *PROP_LSFG_ENABLED = "debug.odinhub.lsfg.on";
constexpr const char *PROP_LSFG_MULT = "debug.odinhub.lsfg.mult";

// Usamos o namespace `debug.*` de propósito: é o único prefixo de propriedades que qualquer
// processo pode LER sem restrições em todas as versões do Android usadas por este projeto,
// mesmo vindo de outro processo/UID (o processo do jogo não tem — nem precisa de ter — a
// mesma identidade da nossa app). ESCREVER em `debug.*` continua a exigir root/shell, que o
// ShellExecutor já usa para tudo neste projeto.

class ConfigReader {
public:
    // Reler a cada frame seria uma syscall (__system_property_get) por propriedade por frame --
    // barato individualmente, mas ainda assim desnecessário a 60-120Hz. Cache com TTL curto: em
    // jogo, uma mudança de definição não precisa de ter efeito no frame seguinte, 100-150ms de
    // atraso é impercetível.
    const LayerConfig &get() {
        auto now = std::chrono::steady_clock::now();
        if (now - lastRefresh_ > std::chrono::milliseconds(120)) {
            refresh();
            lastRefresh_ = now;
        }
        return cached_;
    }

private:
    void refresh() {
        cached_.sgsrEnabled = detail::propBool(PROP_SGSR_ENABLED, false);
        cached_.sgsrMode = detail::propInt(PROP_SGSR_MODE, 0);
        cached_.sgsrSharpness = detail::propFloat(PROP_SGSR_SHARP, 0.5f);
        cached_.reshadeEffectId = detail::propInt(PROP_RESHADE_EFFECT, 0);
        cached_.saturation = detail::propFloat(PROP_RESHADE_SAT, 1.0f);
        cached_.temperature = detail::propFloat(PROP_RESHADE_TEMP, 6500.0f);
        cached_.lsfgEnabled = detail::propBool(PROP_LSFG_ENABLED, false);
        cached_.lsfgMultiplier = detail::propInt(PROP_LSFG_MULT, 2);
    }

    LayerConfig cached_{};
    std::chrono::steady_clock::time_point lastRefresh_{};
};

} // namespace odin
