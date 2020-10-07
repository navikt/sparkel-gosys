package no.nav.helse.sparkel.gosys

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
internal class OppgaveløserTest {

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode
    private lateinit var service: OppgaveService

    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    private val rapid = object : RapidsConnection() {

        fun sendTestMessage(message: String) {
            listeners.forEach { it.onMessage(message, context) }
        }

        override fun publish(message: String) {}

        override fun publish(key: String, message: String) {}

        override fun start() {}

        override fun stop() {}
    }

    @BeforeAll
    fun setup() {
        wireMockServer.start()
        configureFor(create().port(wireMockServer.port()).build())
        stubEksterneEndepunkt()
        service = OppgaveService(
            OppgaveClient(
                baseUrl = wireMockServer.baseUrl(),
                stsClient = StsRestClient(
                    baseUrl = wireMockServer.baseUrl(),
                    serviceUser = ServiceUser("", "")
                )
            )
        )
    }

    @AfterAll
    internal fun teardown() {
        wireMockServer.stop()
    }

    @BeforeEach
    internal fun beforeEach() {
        sendtMelding = objectMapper.createObjectNode()
    }

    @Test
    fun `løser behov`() {
        testBehov(enkeltBehov())

        assertEquals(1, sendtMelding.antall())
        assertFalse(sendtMelding.oppslagFeilet())
    }

    @Test
    fun `løser behov hvor oppslag feiler`() {
        testBehov(behovSomFeiler())

        assertNull(sendtMelding.antall())
        assertTrue(sendtMelding.oppslagFeilet())
    }

    private fun JsonNode.antall() = this.path("@løsning").path(Oppgaveløser.behov).path("antall").takeUnless { it.isNull }?.asInt()
    private fun JsonNode.oppslagFeilet() = this.path("@løsning").path(Oppgaveløser.behov).path("oppslagFeilet").asBoolean()

    private fun testBehov(behov: String) {
        Oppgaveløser(rapid, service)
        rapid.sendTestMessage(behov)
    }

    private fun enkeltBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "ÅpneOppgaver" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "ÅpneOppgaver": {
                "aktørId" : "aktørId"
            }
        }
        """

    private fun behovSomFeiler() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "ÅpneOppgaver" ],
            "@id" : "id2",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "ÅpneOppgaver": {
                "aktørId" : "aktørId"
            }
        }
        """

    private fun stubEksterneEndepunkt() {
        stubFor(
            get(urlPathEqualTo("/rest/v1/sts/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Correlation-ID", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "antallTreffTotalt": 1,
                                      "oppgaver": []
                                    }
                                    """
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/oppgaver"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("X-Correlation-ID", equalTo("id2"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                )
        )
    }
}
