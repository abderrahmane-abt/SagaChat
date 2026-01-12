package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
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
fun ActionProgressButton(
    onClickListener: () -> Unit,
    icon: ImageVector = Icons.Default.Stop,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Circle.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Background circular progress indicator
        CircularProgressIndicator(
            modifier = Modifier.size(rDp(Standards.ActionIconSize)),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = rDp(2.dp),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )

        // Icon button in center
        FilledIconButton(
            onClick = { onClickListener() },
            colors = colors,
            shape = shape,
            modifier = Modifier.size(rDp(Standards.ActionIconSize - 8.dp))
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
            )
        }
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
    icon: Int,
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
        Icon(painterResource(icon), contentDescription)
        Spacer(Modifier.width(rDp(6.dp)))
        Text(text)
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
        contentPadding = PaddingValues(end = rDp(12.dp))
    ) {
        Icon(icon, contentDescription)
        Spacer(Modifier.width(rDp(6.dp)))
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,  // Add this overload
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}
