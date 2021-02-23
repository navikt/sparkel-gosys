package no.nav.helse.sparkel.gosys

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class Oppgaveløser(
    rapidsConnection: RapidsConnection,
    private val oppgaveService: OppgaveService
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val behov = "ÅpneOppgaver"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("ÅpneOppgaver.aktørId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        oppgaveService.løsningForBehov(
            packet["@id"].asText(),
            packet["ÅpneOppgaver.aktørId"].asText()
        ).let { løsning ->
            packet["@løsning"] = mapOf(
                behov to mapOf(
                    "antall" to (løsning?.takeUnless { it.isMissingNode }?.let { it["antallTreffTotalt"].asInt() }),
                    "oppslagFeilet" to (løsning == null)
                )
            )
            context.publish(packet.toJson().also { json ->
                sikkerlogg.info(
                    "sender svar {} for {}",
                    keyValue("id", packet["@id"].asText()),
                    json
                )
            })
        }
    }
}
