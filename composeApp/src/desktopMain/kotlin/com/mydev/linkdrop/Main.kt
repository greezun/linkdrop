package com.mydev.linkdrop


import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LinkDrop"
    ) {
        App() // має бути у commonMain
    }
}