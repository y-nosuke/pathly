package com.pathly.presentation.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pathly.BuildConfig
import com.pathly.R
import com.pathly.presentation.common.MapPlaceFilterButton
import com.pathly.ui.theme.PathlyElevation

/**
 * 経路詳細の上部の丸ボタン（戻る・経路の全体を見る・登録済みの場所・その絞り込み・補正の調整・GPS 詳細）。
 * ソリッドバーは置かず地図の上に浮かせる。
 *
 * @param hasPoints 経路に GPS の点があるか（無ければ地図に関わるボタンは出さない）。
 */
@Composable
internal fun TrackDetailTopButtons(
  hasPoints: Boolean,
  mapPlaces: MapRegisteredPlaces,
  tuningMode: Boolean,
  onBack: () -> Unit,
  onFitTrack: () -> Unit,
  onToggleTuning: () -> Unit,
  onOpenDebugInfo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .statusBarsPadding()
      .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    MapRoundButton(onClick = onBack, icon = R.drawable.ic_arrow_back, contentDescription = "戻る", tinted = false)

    // 経路の全体を見る。立ち寄りへ寄ったり地図を動かしたりしたあと、開いたときの見え方に戻す。
    // 戻るの隣に固定で置く（右に並ぶトグル・絞り込みは出たり消えたりするので、位置がずれないように）。
    if (hasPoints) {
      MapRoundButton(onClick = onFitTrack, icon = R.drawable.ic_fit, contentDescription = "経路の全体を見る")
    }

    // 登録済みの場所を地図に出すトグル（ONで既存placeをアンバーのピンで表示）。
    MapRoundButton(
      onClick = mapPlaces.onToggle,
      icon = R.drawable.ic_place,
      contentDescription = if (mapPlaces.shown) "登録済みの場所を隠す" else "登録済みの場所を表示",
      selected = mapPlaces.shown,
    )

    // 表示 ON のときだけ、隣に絞り込みボタンを出す。
    if (mapPlaces.shown) {
      MapPlaceFilterButton(
        filter = mapPlaces.filter,
        places = mapPlaces.places,
        onFilterChange = mapPlaces.onFilterChange,
      )
    }

    // 補正の調整はデバッグビルドのみ
    if (BuildConfig.DEBUG && hasPoints) {
      MapRoundButton(onClick = onToggleTuning, icon = R.drawable.ic_tune, contentDescription = "補正を調整", selected = tuningMode)
    }

    // GPS点の付随情報を確認するデバッグダイアログを開くボタン（デバッグビルドのみ）。
    if (BuildConfig.DEBUG && hasPoints) {
      MapRoundButton(onClick = onOpenDebugInfo, icon = R.drawable.ic_list, contentDescription = "GPS詳細（デバッグ）")
    }
  }
}

/**
 * 地図の上に浮かせる丸ボタン。[selected] の間は塗りつぶす（トグルが ON であることを示す）。
 * [tinted] が false なら、アイコンの色は既定（Surface の内容色）のまま。
 */
@Composable
private fun MapRoundButton(
  onClick: () -> Unit,
  @DrawableRes icon: Int,
  contentDescription: String,
  selected: Boolean = false,
  tinted: Boolean = true,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    shadowElevation = PathlyElevation.floating,
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = contentDescription,
      tint = when {
        !tinted -> LocalContentColor.current
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
      },
      modifier = Modifier.padding(8.dp),
    )
  }
}
