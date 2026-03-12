package com.khaledabbas.orabi.breadcounting.discovery

import kotlinx.serialization.Serializable

@Serializable
data class TunnelResponse(
    val tunnelUrl: String,
    val updatedAt: String
)
