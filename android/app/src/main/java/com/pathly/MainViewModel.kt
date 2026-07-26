package com.pathly

import androidx.lifecycle.ViewModel
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 画面横断の小さな橋渡し用。場所タブの「関連経路」から経路詳細を開くとき、
 * trackId から [GpsTrack] を解決するために使う。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository,
) : ViewModel() {
  suspend fun getTrack(id: Long): GpsTrack? = gpsTrackRepository.getTrackById(id)
}
