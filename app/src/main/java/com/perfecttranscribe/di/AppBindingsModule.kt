package com.perfecttranscribe.di

import com.perfecttranscribe.api.GroqRepository
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.AudioRecorder
import com.perfecttranscribe.audio.Recorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bindRecorder(audioRecorder: AudioRecorder): Recorder

    @Binds
    @Singleton
    abstract fun bindTranscriptionRepository(
        groqRepository: GroqRepository,
    ): TranscriptionRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(apiKeyManager: ApiKeyManager): ApiKeyStore
}
