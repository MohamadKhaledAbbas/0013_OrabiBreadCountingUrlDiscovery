package com.khaledabbas.orabi.breadcounting.discovery

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform