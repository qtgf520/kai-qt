package com.qtkai.zhong.build

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtkai.zhong.build.terminal.TerminalKey
import com.qtkai.zhong.build.terminal.TerminalModifiers

/** Kai Build is Android-only; there is no environment to type into here. */
actual val supportsRawTerminalInput: Boolean = false

@Composable
actual fun PlatformTerminalKeyboard(
    showKeyboardRequest: Int,
    onKey: (TerminalKey, TerminalModifiers) -> Unit,
    onText: (String, TerminalModifiers) -> Unit,
    modifier: Modifier,
) = Unit
