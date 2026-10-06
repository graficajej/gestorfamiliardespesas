package co.jmessias.gestor_familiar_despesas.presentation.management

import kotlin.test.Test
import kotlin.test.assertEquals

class ManagementErrorMessageTest {

    @Test
    fun `domain validation messages are preserved`() {
        assertEquals(
            "Informe um valor maior que zero.",
            IllegalArgumentException("Informe um valor maior que zero.")
                .toPresentationMessage("Não foi possível salvar."),
        )
    }

    @Test
    fun `unexpected technical messages are replaced with a safe fallback`() {
        assertEquals(
            "Não foi possível salvar.",
            IllegalStateException("SQLite constraint failed: secret detail")
                .toPresentationMessage("Não foi possível salvar."),
        )
        assertEquals(
            "Não foi possível salvar.",
            RuntimeException("Unexpected database failure")
                .toPresentationMessage("Não foi possível salvar."),
        )
    }
}
