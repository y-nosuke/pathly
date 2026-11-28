package com.pathly.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * 重い計算（経路の線の組み立てなど）に使うディスパッチャー。
 *
 * ViewModel の中で `Dispatchers.Default` を直に使うと、テストで差し替えられない。テストが終わって
 * `Dispatchers.resetMain()` した後に、そこで走っていた処理が Main へ戻ろうとして例外になり、次のテストが
 * 「テストの前に処理されない例外があった」で落ちる（CI でだけ時々起きた）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComputeDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
  @Provides
  @ComputeDispatcher
  fun provideComputeDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
