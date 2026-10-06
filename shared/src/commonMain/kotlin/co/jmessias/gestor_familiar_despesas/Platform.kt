package co.jmessias.gestor_familiar_despesas

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform