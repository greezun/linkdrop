package com.mydev.linkdrop

interface DeviceIdStore {
    fun getOrCreate(): String
}

