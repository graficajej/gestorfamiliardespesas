package co.jmessias.gestor_familiar_despesas.domain.money

@JvmInline
value class Money private constructor(val cents: Long) : Comparable<Money> {
    init {
        require(cents >= 0) { "Money cannot be negative" }
    }

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        val ZERO: Money = Money(0)

        fun ofCents(cents: Long): Money {
            require(cents >= 0) { "Money cents cannot be negative" }
            return Money(cents)
        }

        fun ofReais(reais: Long, centavos: Long = 0): Money {
            require(reais >= 0) { "Reais cannot be negative" }
            require(centavos in 0..99) { "Centavos must be between 0 and 99" }
            return Money(reais * 100 + centavos)
        }
    }
}
