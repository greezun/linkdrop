package com.mydev.linkdrop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform