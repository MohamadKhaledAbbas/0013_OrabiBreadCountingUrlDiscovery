package com.khaledabbas.orabi.breadcounting.discovery

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}