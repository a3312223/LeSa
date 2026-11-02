package com.leshang.lesa.core.assembly

import com.leshang.lesa.core.cap.mcp.McpCapability
import com.leshang.lesa.core.cap.mcp.McpConnection
import com.leshang.lesa.core.run.RunSpec

internal class McpClientProvider(private val connection: McpConnection) : CapabilityProvider {
    override val id: String = "mcp:${connection.endpoint.serverId}"
    override fun open(spec: RunSpec) = McpCapability(connection, id)
}
