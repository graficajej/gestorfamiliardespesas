package co.jmessias.gestor_familiar_despesas.domain.model

import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface EntityMetadataProvider {
    fun newId(): String
    fun nowUtc(): String
    fun today(): LocalDate
}

object SystemEntityMetadataProvider : EntityMetadataProvider {
    override fun newId(): String = Uuid.random().toString()

    override fun nowUtc(): String = Clock.System.now().toString()

    override fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
