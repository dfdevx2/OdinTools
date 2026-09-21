package de.langerhans.odintools.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fonte única de verdade, **reativa**, sobre qual app está em primeiro plano.
 *
 * CAUSA RAIZ que esta classe resolve (bug relatado: "o overlay e o banco de dados não estão
 * interconectados"): o overlay lia o pacote atual com `val currentApp = prefs.currentForegroundApp`
 * — uma leitura simples de SharedPreferences, que **não é um `State` do Compose**. Como a
 * `ComposeView` do overlay é criada UMA única vez (no `onCreate` do [de.langerhans.odintools.service.GamingOverlayService])
 * e nunca mais recompõe por causa disso, `currentApp` ficava congelado no valor que estivesse
 * gravado quando o serviço arrancou — normalmente `"global"`, ou o jogo anterior. Resultado: o
 * utilizador abria o GTA, punha 5 W no overlay, e a linha era gravada no Room **sob o pacote
 * errado**. O ecrã "Per-App Overrides" nunca mostrava nada para o GTA porque, do ponto de vista do
 * banco, aquela regra nunca foi criada para o GTA.
 *
 * Com um [StateFlow], o overlay passa a observar (`collectAsState`) e a sua composição re-chaveia
 * sozinha a cada troca de jogo: os `remember(currentApp)` voltam a ler os valores do jogo certo, e
 * as escritas passam a cair na linha certa do banco.
 *
 * [isGameForeground] responde ao segundo bug relatado ("o overlay só deve aparecer dentro do
 * jogo, igual aos modos game de celular"): começa em `false` (antes começava em `true`, e por isso
 * a barrinha aparecia na home até o serviço de acessibilidade disparar pela primeira vez) e só
 * passa a `true` quando o app em primeiro plano é mesmo um app de utilizador.
 */
@Singleton
class ForegroundAppTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Pacote do app de utilizador atualmente em primeiro plano, ou [GLOBAL] fora de qualquer app. */
    private val _currentPackage = MutableStateFlow(GLOBAL)
    val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

    /**
     * `true` só quando há um app/jogo de utilizador em primeiro plano. Arranca em `false` de
     * propósito: a barrinha do overlay nunca deve aparecer antes de sabermos que estamos mesmo
     * dentro de um app.
     */
    private val _isGameForeground = MutableStateFlow(false)
    val isGameForeground: StateFlow<Boolean> = _isGameForeground.asStateFlow()

    /**
     * Eventos de acessibilidade chegam às dezenas por segundo em algumas apps; resolver intents no
     * PackageManager a cada um seria caro. O veredito por pacote nunca muda enquanto a app estiver
     * instalada, portanto fica em cache.
     */
    private val trackableCache = ConcurrentHashMap<String, Boolean>()

    /**
     * Todos os pacotes que conseguem atuar como ecrã inicial. Resolvido preguiçosamente e uma só
     * vez: usar `resolveActivity` sozinho devolveria apenas o launcher ativo, e em aparelhos com
     * mais de um launcher instalado (muito comum em handhelds — Daijisho, ES-DE, o launcher de
     * fábrica da AYN) os outros continuariam a contar como "jogo".
     */
    private val homePackages: Set<String> by lazy {
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "Falha ao resolver os launchers do sistema", it)
            emptySet()
        }
    }

    /**
     * Decide se um pacote conta como "app de utilizador" (jogo, emulador, app normal) para efeitos
     * de overlay e de regras por app.
     *
     * Antes isto era `pkg != launcherPackage` mais um filtro `pkg.contains("odintools")`. Os dois
     * estavam errados:
     *  - O `applicationId` real desta app é `com.dfdx047.odinhub` (o `namespace` Kotlin é que
     *    continua `de.langerhans.odintools`), por isso `contains("odintools")` **nunca** dava
     *    verdade e o próprio Odin Hub era tratado como um jogo — com barrinha do overlay por cima
     *    do próprio app e regras por app a serem criadas para ele.
     *  - `!= launcherPackage` deixava passar Definições, diálogos do sistema, teclados, a folha de
     *    permissões, etc.
     *
     * O critério agora é: não somos nós, não é um launcher, não é a UI do sistema, e o pacote tem
     * um *launcher intent* próprio — ou seja, é algo que o utilizador consegue abrir a partir da
     * gaveta de apps. Diálogos do sistema, IMEs e componentes internos não têm.
     */
    fun isTrackableApp(pkg: String): Boolean = trackableCache.getOrPut(pkg) {
        when {
            pkg.isBlank() -> false
            pkg == context.packageName -> false
            pkg == ANDROID_SYSTEM -> false
            pkg.endsWith(".systemui") -> false
            pkg in homePackages -> false
            else -> runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
        }
    }

    /**
     * Chamado pelo watcher de acessibilidade a cada mudança de app em primeiro plano.
     * Devolve `true` quando o novo pacote é um app de utilizador (e portanto as regras por app
     * devem ser aplicadas); `false` quando voltámos para a home/sistema/para esta própria app.
     */
    fun onForegroundPackage(pkg: String): Boolean {
        val trackable = isTrackableApp(pkg)
        // Deliberadamente NÃO persistimos isto em SharedPreferences. O antigo
        // `prefs.currentForegroundApp` era precisamente a origem do bug: um valor guardado em
        // disco que sobrevivia ao processo e era lido mais tarde como se ainda fosse verdade,
        // quando o app em primeiro plano é, por natureza, informação do momento. Ao arrancar não
        // há nenhum app em primeiro plano conhecido até o serviço de acessibilidade reportar um
        // (ver ForegroundAppWatcherService.onServiceConnected).
        _currentPackage.value = if (trackable) pkg else GLOBAL
        _isGameForeground.value = trackable
        return trackable
    }

    companion object {
        private const val TAG = "ForegroundAppTracker"
        private const val ANDROID_SYSTEM = "android"

        /**
         * Sentinela para "nenhum app de utilizador em primeiro plano". NUNCA deve ser gravado como
         * `packageName` no Room — ver a guarda em `QuickAccessContent.persistOverride`, que existe
         * precisamente porque era sob este valor que as regras do jogo acabavam a ser gravadas.
         */
        const val GLOBAL = "global"
    }
}
