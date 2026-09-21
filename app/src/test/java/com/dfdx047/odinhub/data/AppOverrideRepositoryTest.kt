package com.dfdx047.odinhub.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes do [AppOverrideRepository] -- a fonte única de verdade das regras por jogo (ver o
 * comentário na própria classe sobre os dois armazenamentos paralelos que existiam antes desta
 * auditoria). Corre contra um Room *in-memory* via Robolectric, para apanhar regressões de
 * caching/upsert/delete como testes JVM comuns (a mesma `testDebugUnitTest` que a CI já corre),
 * sem depender de um dispositivo/emulador físico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppOverrideRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AppOverrideRepository

    private fun sampleEntity(pkg: String, tdp: Float = 15f) = AppOverrideEntity(
        packageName = pkg,
        tdpProfile = "Balanced",
        clockProfile = null,
        fanProfile = null,
        tdpWatts = tdp,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Cada teste começa com um banco vazio e isolado, sem tocar disco nem deixar resíduo
        // entre execuções.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppOverrideRepository(db.appOverrideDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `get returns null for a package with no override`() {
        assertNull(repository.get("com.unknown.app"))
        assertFalse(repository.has("com.unknown.app"))
    }

    @Test
    fun `upsert makes the entity readable synchronously via cache`() = runBlocking {
        val pkg = "com.example.game"
        repository.upsert(sampleEntity(pkg, tdp = 12f))

        // O cache em memória (overridesByPackage) é alimentado pelo Flow do Room em background --
        // esperamos aqui pela primeira emissão que já contém o pacote, em vez de assumir que
        // `get()` já teria o valor imediatamente após o `upsert` suspender.
        val cached = repository.overridesByPackage.first { it[pkg] != null }[pkg]

        assertEquals(12f, cached?.tdpWatts)
        assertTrue(repository.has(pkg))
        assertEquals(cached, repository.get(pkg))
    }

    @Test
    fun `upsert with same package name replaces the previous override`() = runBlocking {
        val pkg = "com.example.game"
        repository.upsert(sampleEntity(pkg, tdp = 10f))
        repository.overridesByPackage.first { it[pkg]?.tdpWatts == 10f }

        repository.upsert(sampleEntity(pkg, tdp = 20f))
        val updated = repository.overridesByPackage.first { it[pkg]?.tdpWatts == 20f }[pkg]

        assertEquals(20f, updated?.tdpWatts)
    }

    @Test
    fun `delete removes the override from cache`() = runBlocking {
        val pkg = "com.example.game"
        repository.upsert(sampleEntity(pkg))
        repository.overridesByPackage.first { it[pkg] != null }

        repository.delete(pkg)
        repository.overridesByPackage.first { !it.containsKey(pkg) }

        assertNull(repository.get(pkg))
        assertFalse(repository.has(pkg))
    }

    @Test
    fun `different packages are cached independently`() = runBlocking {
        repository.upsert(sampleEntity("com.pkg.one", tdp = 5f))
        repository.upsert(sampleEntity("com.pkg.two", tdp = 25f))

        repository.overridesByPackage.first { it.size >= 2 }

        assertEquals(5f, repository.get("com.pkg.one")?.tdpWatts)
        assertEquals(25f, repository.get("com.pkg.two")?.tdpWatts)
    }
}
