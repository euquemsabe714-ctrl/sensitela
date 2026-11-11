package com.example.setas

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        col.addView(TextView(this).apply {
            text = "Setas na Tela"
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(12), 0, dp(12))
        }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "Ativar nas configurações de Acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        col.addView(TextView(this).apply {
            text = "Na lista, procure por \"Setas na Tela\" (pode estar em \"Apps instalados\"), " +
                "abra e ligue o serviço. Depois volte aqui."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        })

        col.addView(Switch(this).apply {
            text = "Mostrar os botões na tela"
            textSize = 16f
            isChecked = prefs.getBoolean(Prefs.ENABLED, true)
            setPadding(0, dp(16), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.ENABLED, checked).apply()
            }
        })

        slider(col, "Tamanho dos botões", 40, 100, Prefs.SIZE, 60, " dp")
        slider(col, "Some depois de", 2, 20, Prefs.TIMEOUT, 4, " s")
        slider(col, "Opacidade quando visível", 30, 100, Prefs.OPACITY, 85, "%")

        col.addView(Button(this).apply {
            text = "Voltar os botões para a posição padrão"
            setOnClickListener {
                prefs.edit()
                    .remove(Prefs.X)
                    .remove(Prefs.Y)
                    .putLong(Prefs.RESET, System.currentTimeMillis())
                    .apply()
            }
        })

        col.addView(TextView(this).apply {
            text = "Como usar\n" +
                "• Setas: tocam e rolam a tela. Segure para rolar continuamente.\n" +
                "• OK: funciona como o Enter (clica no item selecionado; se não houver, toca no centro da tela).\n" +
                "• \"≡ mover\": arraste para mudar os botões de lugar.\n" +
                "• Sem uso, os botões ficam invisíveis. Toque na área deles uma vez para acordar (esse primeiro toque não aciona nada).\n" +
                "• Tudo funciona offline. Nenhum dado sai do celular."
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun slider(
        parent: LinearLayout, title: String, lo: Int, hi: Int,
        key: String, def: Int, unit: String
    ) {
        val value = prefs.getInt(key, def).coerceIn(lo, hi)
        val label = TextView(this).apply {
            text = "$title: $value$unit"
            textSize = 16f
            setPadding(0, dp(16), 0, 0)
        }
        val bar = SeekBar(this).apply {
            max = hi - lo
            progress = value - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    label.text = "$title: ${lo + p}$unit"
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {
                    prefs.edit().putInt(key, lo + (s?.progress ?: 0)).apply()
                }
            })
        }
        parent.addView(label)
        parent.addView(bar)
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, SetasService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refreshStatus() {
        val on = serviceEnabled()
        status.text = if (on) "✔ Serviço ativo. Os botões já devem estar na tela."
        else "✖ Serviço desligado. Toque no botão abaixo e ligue \"Setas na Tela\"."
        status.setTextColor(if (on) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }
}
