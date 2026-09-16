package com.elder.android.agent

import android.content.Context

interface PromptProvider {
    fun system(): String

    fun save(): String
}

class AgentPrompts(private val context: Context) : PromptProvider {
    override fun system(): String = read(SYSTEM_PROMPT)

    override fun save(): String = read(SAVE_PROMPT)

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val VERSION = "v1"
        private const val SYSTEM_PROMPT = "agent/system_v1.txt"
        private const val SAVE_PROMPT = "agent/save_v1.txt"
    }
}
