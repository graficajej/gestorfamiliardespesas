package co.jmessias.gestor_familiar_despesas.data.bootstrap

import co.jmessias.gestor_familiar_despesas.db.AppDatabase
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.FamilyBootstrapRepository
import co.jmessias.gestor_familiar_despesas.domain.bootstrap.OwnerFamilySetup
import co.jmessias.gestor_familiar_despesas.domain.model.EntityMetadataProvider
import co.jmessias.gestor_familiar_despesas.domain.model.SystemEntityMetadataProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightFamilyBootstrapRepository(
    private val database: AppDatabase,
    private val metadataProvider: EntityMetadataProvider = SystemEntityMetadataProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FamilyBootstrapRepository {
    private val queries = database.appDatabaseQueries

    override suspend fun loadOwnerFamily(): OwnerFamilySetup? = withContext(dispatcher) {
        queries.selectOwnerFamily().executeAsOneOrNull()?.let { row ->
            OwnerFamilySetup(
                userId = row.user_id,
                familyId = row.family_id,
                ownerName = row.owner_name,
                familyName = row.family_name,
            )
        }
    }

    override suspend fun saveOwnerFamily(setup: OwnerFamilySetup) = withContext(dispatcher) {
        val now = metadataProvider.nowUtc()
        val memberId = metadataProvider.newId()

        database.transaction {
            queries.insertUser(
                id = setup.userId,
                name = setup.ownerName,
                email = null,
                created_at = now,
                updated_at = now,
            )
            queries.insertFamily(
                id = setup.familyId,
                name = setup.familyName,
                created_at = now,
                updated_at = now,
            )
            queries.insertFamilyMember(
                id = memberId,
                family_id = setup.familyId,
                user_id = setup.userId,
                role = "OWNER",
                created_at = now,
                updated_at = now,
            )
        }
        Unit
    }
}
