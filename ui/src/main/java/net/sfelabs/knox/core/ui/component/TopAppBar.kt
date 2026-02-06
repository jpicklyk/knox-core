package net.sfelabs.knox.core.ui.component

import android.R
import androidx.annotation.StringRes
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnoxTopAppBar(
    @StringRes titleResource: Int,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
) {
    CenterAlignedTopAppBar(
        title = { Text(text = stringResource(id = titleResource)) },
        colors = colors,
        modifier = modifier,
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun KnoxTopAppBarPreview() {
    KnoxTopAppBar(
        titleResource = R.string.untitled
    )
}