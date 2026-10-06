package co.jmessias.gestor_familiar_despesas.domain.bootstrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CreateOwnerFamilyUseCaseTest {

    @Test
    fun `blank owner name is rejected`() = runTest {
        val result = CreateOwnerFamilyUseCase(FakeFamilyBootstrapRepository(), FixedMetadataProvider())
            .execute(OwnerFamilySeed(ownerName = "   ", familyName = "Familia"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `owner and family names are normalized and persisted`() = runTest {
        val repository = FakeFamilyBootstrapRepository()
        val result = CreateOwnerFamilyUseCase(repository, FixedMetadataProvider())
            .execute(OwnerFamilySeed(ownerName = "  Ana  ", familyName = "  Santos  "))

        assertTrue(result.isSuccess)
        val setup = result.getOrThrow()
        assertEquals("user-1", setup.userId)
        assertEquals("family-1", setup.familyId)
        assertEquals("Ana", setup.ownerName)
        assertEquals("Santos", setup.familyName)
        assertEquals(setup, repository.saved)
    }

    private class FakeFamilyBootstrapRepository : FamilyBootstrapRepository {
        var saved: OwnerFamilySetup? = null

        override suspend fun loadOwnerFamily(): OwnerFamilySetup? = saved

        override suspend fun saveOwnerFamily(setup: OwnerFamilySetup) {
            saved = setup
        }
    }

    private class FixedMetadataProvider : co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider {
        private var nextId = 0

        override fun newId(): String = when (++nextId) {
            1 -> "user-1"
            else -> "family-1"
        }

        override fun nowUtc(): String = "2026-10-06T12:00:00Z"

        override fun today() = kotlinx.datetime.LocalDate(2026, 10, 6)
    }
}
