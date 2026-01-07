package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.theme.rDp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButton(
    onClickListener: () -> Unit,
    icon: Int,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    FilledIconButton(
        onClick = { onClickListener() },
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButton(
    onClickListener: () -> Unit,
    icon: ImageVector,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    FilledIconButton(
        onClick = { onClickListener() },
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

@SuppressLint("ModifierParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionTextButton(
    onClickListener: () -> Unit,
    icon: ImageVector,
    text: String,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    ),
    shape: Shape = RoundedCornerShape(rDp(6.dp))
) {
    FilledTonalButton(
        onClick = onClickListener,
        shape = shape,
        colors = colors,
        modifier = modifier.height(rDp(Standards.ActionIconSize)),
        contentPadding = PaddingValues(horizontal = rDp(12.dp))
    ) {
        Icon(icon, contentDescription)
        Spacer(Modifier.width(rDp(6.dp)))
        Text(text)
    }
}
