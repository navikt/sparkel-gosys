package no.nav.helse.sparkel.gosys

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import java.io.File

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

internal fun createApp(env: Map<String, String>): RapidsConnection {
    val stsClient = StsRestClient(
        baseUrl = env.getValue("STS_BASE_URL"),
        serviceUser = "/var/run/secrets/nais.io/service_user".let { ServiceUser("$it/username".readFile(), "$it/password".readFile()) }
    )
    val oppgaveClient = OppgaveClient(
        baseUrl = env.getValue("OPPGAVE_URL"),
        stsClient = stsClient
    )
    val oppgaveService = OppgaveService(oppgaveClient)

    return RapidApplication.create(env).apply {
        Oppgaveløser(this, oppgaveService)
    }
}

private fun String.readFile() = File(this).readText(Charsets.UTF_8)
