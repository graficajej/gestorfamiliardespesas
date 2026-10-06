package co.jmessias.gestor_familiar_despesas.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `money stores values in cents`() {
        val money = Money.ofCents(1250)
        assertEquals(1250L, money.cents)
    }

    @Test
    fun `money can be created from reais and centavos`() {
        val money = Money.ofReais(10, 50)
        assertEquals(1050L, money.cents)
    }

    @Test
    fun `money rejects negative values`() {
        assertFailsWith<IllegalArgumentException> {
            Money.ofCents(-1)
        }
    }

    @Test
    fun `money sums and subtracts deterministically`() {
        val total = Money.ofCents(1000) + Money.ofCents(250)
        val reduced = Money.ofCents(1500) - Money.ofCents(250)

        assertEquals(1250L, total.cents)
        assertEquals(1250L, reduced.cents)
    }
}
