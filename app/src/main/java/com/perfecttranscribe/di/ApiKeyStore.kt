package com.perfecttranscribe.di

interface ApiKeyStore {
    fun getApiKey(): String?

    fun saveApiKey(key: String)

    fun hasApiKey(): Boolean

    fun clearApiKey()

    fun getModel(): String

    fun saveModel(model: String)
}
