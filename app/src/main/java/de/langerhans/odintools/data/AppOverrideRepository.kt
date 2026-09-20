package de.langerhans.odintools.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fonte única de verdade para as regras por jogo (TDP/Clock/Fan/LSFG/SGSR/ReShade).
 *
 * Antes desta auditoria existiam DOIS armazenamentos paralelos e desligados um do outro:
 *  - A tabela Room `AppOverrideEntity`, escrita apenas pelo ecrã "Per-App Overrides" (aba
 *    Performance), guardando só NOMES de perfil (ex: "Balanced") sem nenhum valor numérico.
 *  - Chaves soltas no SharedPrefs (`override_<pkg>_tdp`, etc.), escritas apenas pelo overlay
 *    em jogo, e são estas — não a tabela Room — que o `ForegroundAppWatcherService`
 *    efetivamente lia para aplicar os limites.
 * Resultado: configurar um jogo na aba Performance não tinha NENHUM efeito em jogo, porque
 * nada nunca traduzia o nome do perfil escolhido ali para os números que o motor de hardware
 * usa. Este repositório substitui os dois: os valores numéricos passam a viver só no Room
 * (`AppOverrideEntity.tdpWatts/perfClockKHz/...`), e tanto o overlay como o watcher leem e
 * escrevem exclusivamente através daqui.
 *
 * Expomos um [StateFlow] em cache (por pacote) para que o [de.langerhans.odintools.service.ForegroundAppWatcherService]
 * -- que corre no thread de eventos de acessibilidade e não pode esperar por uma query
 * suspend a cada troca de app -- consiga ler o último valor conhecido de forma síncrona via
 * `.value`, sem nunca bloquear esse thread em I/O de disco.
 */
@Singleton
class AppOverrideRepository @Inject constructor(
    private val dao: AppOverrideDao,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val overridesByPackage: StateFlow<Map<String, AppOverrideEntity>> =
        dao.getAll()
            .map { list -> list.associateBy { it.packageName } }
            .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    /** Leitura síncrona e não-bloqueante a partir do cache em memória (sempre atualizado pelo Flow do Room). */
    fun get(packageName: String): AppOverrideEntity? = overridesByPackage.value[packageName]

    fun has(packageName: String): Boolean = get(packageName) != null

    suspend fun upsert(entity: AppOverrideEntity) = withContext(Dispatchers.IO) {
        dao.save(entity)
    }

    suspend fun delete(packageName: String) = withContext(Dispatchers.IO) {
        dao.deleteByPackageName(packageName)
    }
}
