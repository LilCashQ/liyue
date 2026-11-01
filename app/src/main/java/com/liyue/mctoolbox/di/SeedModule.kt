package com.liyue.mctoolbox.di

import com.liyue.mctoolbox.seedgen.SeedEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeedModule {
    @Provides
    @Singleton
    fun provideSeedEngine(): SeedEngine = SeedEngine()
}
