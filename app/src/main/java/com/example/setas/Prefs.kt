package com.example.setas

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val ENABLED = "enabled"      // botões ligados?
    const val SIZE = "size_dp"         // tamanho de cada botão (dp)
    const val TIMEOUT = "timeout_s"    // segundos até sumir
    const val OPACITY = "opacity"      // opacidade quando visível (%)
    const val X = "pos_x"              // posição salva
    const val Y = "pos_y"
    const val RESET = "reset_tick"     // usado para recolocar na posição padrão

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("setas", Context.MODE_PRIVATE)
}
