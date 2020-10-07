package no.nav.helse.sparkel.gosys

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class OppgaveClient(
    private val baseUrl: String,
    private val stsClient: StsRestClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
    }

    internal fun hentÅpneOppgaver(
        aktørId: String,
        behovId: String
    ): JsonNode {

        val url =
            "${baseUrl}/api/v1/oppgaver?statuskategori=AAPEN&tema=SYK&aktoerId=${aktørId}&limit=1"

        val (responseCode, responseBody) = with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer ${stsClient.token()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Correlation-ID", behovId)

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        if (responseCode >= 300 || responseBody == null) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from oppgave")
        }

        return objectMapper.readTree(responseBody)
    }
}
