package com.github.lightjunction.magicbox
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class CloudContractTest {
    private val node = """{"outbounds":[{"type":"socks","tag":"one","server":"127.0.0.1","server_port":1080}]}"""
    private fun rejects(block: () -> Unit) { try { block(); fail("expected rejection") } catch (_: CloudFailure) { } }
    @Test fun machineEnvelopeRequiresExactTypesAndOneObject() {
        assertNotNull(machineData("""{"schema":1,"ok":true,"command":"service.status","data":{}}""", "service.status"))
        for (value in listOf("""{"schema":"1","ok":true,"command":"service.status","data":{}}""", """{"schema":1,"ok":false,"command":"service.status","data":{}}""", """{"schema":1,"ok":true,"command":"other","data":{}}""", "{}{}", "{} trailing", "[]")) rejects { machineData(value,"service.status") }
    }
    @Test fun boundedJsonRejectsDepthBeforeRecursiveParsing() { rejects { cloudJson("{\"x\":".repeat(40)+"0"+"}".repeat(40)) }; rejects { cloudJson("{\"x\":1}//comment") } }
    @Test fun importedNodesCannotReadRootFiles() { rejects { safeNodeDocument(JSONObject(node.replace("\"server_port\":1080","\"server_port\":1080,\"tls\":{\"certificate_path\":\"/data/private\"}"))) } }
    @Test fun duplicateAndReservedTagsFail() { rejects { safeNodeDocument(JSONObject(node.replace("one",CLOUD_SELECTOR))) }; rejects { safeNodeDocument(JSONObject("""{"outbounds":[{"type":"socks","tag":"x"},{"type":"socks","tag":"x"}]}""")) } }
    @Test fun systemModeIsLoopbackAndNeverInstallsATun() {
        val config=makeCoreConfig(safeNodeDocument(JSONObject(node)),"system","one","private-key","/sys/fs/cgroup")
        assertEquals(1,config.getJSONArray("inbounds").length())
        assertEquals("127.0.0.1",config.getJSONArray("inbounds").getJSONObject(0).getString("listen"))
        assertEquals("private-key",config.getJSONObject("experimental").getJSONObject("clash_api").getString("secret"))
    }
    @Test fun ebpfDoesNotPretendToBeATun() {
        val config=makeCoreConfig(safeNodeDocument(JSONObject(node)),"ebpf","one","s","/sys/fs/cgroup")
        val inbound=config.getJSONArray("inbounds").getJSONObject(1)
        assertEquals("ebpf",inbound.getString("type"));assertFalse(inbound.has("interface_name"))
    }
    @Test fun emptyAndDirectOnlyCannotConnect() { rejects { safeNodeDocument(JSONObject("{}")) }; rejects { safeNodeDocument(JSONObject("""{"outbounds":[{"type":"direct","tag":"direct"}]}""")) } }
}
